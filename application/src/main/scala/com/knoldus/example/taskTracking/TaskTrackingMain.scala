package com.knoldus.example.taskTracking

import java.time.Instant

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.digitalasset.api.util.TimeProvider
import com.digitalasset.grpc.adapter.AkkaExecutionSequencerPool
import com.digitalasset.ledger.api.refinements.ApiTypes.{ApplicationId, WorkflowId}
import com.digitalasset.ledger.api.v1.ledger_offset.LedgerOffset
import com.digitalasset.ledger.client.LedgerClient
import com.digitalasset.ledger.client.binding.{Contract, Primitive}
import com.digitalasset.ledger.client.configuration.{CommandClientConfiguration, LedgerClientConfiguration, LedgerIdRequirement}
import com.knoldus.example.iou.ClientUtil
import com.knoldus.example.iou.ClientUtil.workflowIdFromParty
import com.knoldus.example.iou.DecodeUtil.{decodeAllCreated, decodeArchived, decodeCreated}
import com.knoldus.example.iou.FutureUtil.toFuture
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

import com.digitalasset.ledger.client.binding.{Primitive => P}
import com.knoldus.example.iou.model.{TaskTracking => M}

object TaskTrackingMain extends App with StrictLogging {

  if (args.length != 2) {
    logger.error("Usage: LEDGER_HOST LEDGER_PORT")
    System.exit(-1)
  }

  private val ledgerHost = args(0)
  private val ledgerPort = args(1).toInt

  private val creator = P.Party("ScrumMaster")
  private val newOwner = P.Party("Dev")
  private val asys = ActorSystem()
  private val amat = Materializer(asys)
  private val aesf = new AkkaExecutionSequencerPool("clientPool")(asys)

  private def shutdown(): Unit = {
    logger.info("Shutting down...")
    Await.result(asys.terminate(), 10.seconds)
    ()
  }

  private implicit val ec: ExecutionContext = asys.dispatcher

  private val applicationId = ApplicationId("Task Manager Example")

  private val timeProvider = TimeProvider.Constant(Instant.EPOCH)

  private val clientConfig = LedgerClientConfiguration(
    applicationId = ApplicationId.unwrap(applicationId),
    ledgerIdRequirement = LedgerIdRequirement("", enabled = false),
    commandClient = CommandClientConfiguration.default,
    sslContext = None,
    token = None
  )

  private val clientF: Future[LedgerClient] =
    LedgerClient.singleHost(ledgerHost, ledgerPort, clientConfig)(ec, aesf)

  private val clientUtilF: Future[ClientUtil] =
    clientF.map(client => new ClientUtil(client, applicationId, 30.seconds, timeProvider))

  private val offset0F: Future[LedgerOffset] = clientUtilF.flatMap(_.ledgerEnd)

  private val issuerWorkflowId: WorkflowId = workflowIdFromParty(creator)
  private val newOwnerWorkflowId: WorkflowId = workflowIdFromParty(newOwner)

  val newOwnerAcceptsAllTransfers: Future[Unit] = for {
    clientUtil <- clientUtilF
    offset0 <- offset0F
    _ <- clientUtil.subscribe(newOwner, offset0, None) { tx => {
      logger.info("\n\n\n\nTRANSACTION RECEIVED\n")
      Thread.sleep(1000);
      logger.info(s"\n\n\n\n$newOwner received transaction: $tx\n\n")
    }
      decodeCreated[M.TaskTrackingTransfer](tx).foreach { contract: Contract[M.TaskTrackingTransfer] =>
        logger.info(s"$newOwner received contract: $contract\n\n")
        val exerciseCmd = contract.contractId.exerciseTaskTrackingTransfer_Accept(actor = newOwner)
        clientUtil.submitCommand(newOwner, newOwnerWorkflowId, exerciseCmd) onComplete {
          case Success(_) =>
            logger.info(s"\n\n\n$newOwner sent exercise command: $exerciseCmd\n")
            logger.info(s"\n\n\n$newOwner accepted IOU Transfer: $contract\n")
          case Failure(e) =>
            logger.error(s"$newOwner failed to send exercise command: $exerciseCmd", e)
        }
      }
    }(amat)
  } yield ()

  // <doc-ref:taskTracking-contract-instance>
  val taskTracking = M.TaskTracking(
    issuer = creator,
    owner = creator,
    task = "task for Dev 1",
    timeEstimate = BigDecimal("16.00"),
    observers = List())
  // </doc-ref:taskTracking-contract-instance>

  val issuerFlow: Future[Unit] = for {
    clientUtil <- clientUtilF
    offset0 <- offset0F
    _ = logger.info(s"Client API initialization completed, Ledger ID: ${clientUtil.toString}")

    createCmd = taskTracking.create
    _ <- clientUtil.submitCommand(creator, issuerWorkflowId, createCmd)
    _ = logger.info(s"$creator created IOU: $taskTracking\n")
    _ = logger.info(s"$creator sent create command: $createCmd\n")

    tx0 <- clientUtil.nextTransaction(creator, offset0)(amat)
    _ = logger.info(s"$creator received transaction: $tx0")
    taskTrackingContract <- toFuture(decodeCreated[M.TaskTracking](tx0))
    _ = logger.info(s"$creator received contract: $taskTrackingContract")

    offset1 <- clientUtil.ledgerEnd

    exerciseCmd = taskTrackingContract.contractId.exerciseTaskTracking_Transfer(actor = creator, newOwner = newOwner)
    _ <- clientUtil.submitCommand(creator, issuerWorkflowId, exerciseCmd)
    _ = logger.info(s"$creator sent exercise command: $exerciseCmd\n\n")
    _ = logger.info(s"$creator transferred IOU: $taskTrackingContract to: $newOwner\n\n")

    tx1 <- clientUtil.nextTransaction(creator, offset1)(amat)
    _ = logger.info(s"$creator received final transaction: $tx1")
    archivedTaskTrackingContractId <- toFuture(decodeArchived[M.TaskTracking](tx1)): Future[P.ContractId[M.TaskTracking]]
    _ = logger.info(
      s"$creator received Archive Event for the original IOU contract ID: $archivedTaskTrackingContractId\n\n")
    _ <- Future(assert(taskTrackingContract.contractId == archivedTaskTrackingContractId))
    taskTrackingTransferContract <- toFuture(decodeAllCreated[M.TaskTrackingTransfer](tx1).headOption)
    _ = logger.info(s"$creator received confirmation for the IOU Transfer: $taskTrackingTransferContract\n\n")

  } yield ()

  val returnCodeF: Future[Int] = issuerFlow.transform {
    case Success(_) =>
      logger.info("\n\n\nIOU flow completed!!!")
      Success(0)
    case Failure(e) =>
      logger.error("IOU flow completed with an error", e)
      Success(1)
  }

  val returnCode: Int = Await.result(returnCodeF, 10.seconds)
  shutdown()
  System.exit(returnCode)
}
