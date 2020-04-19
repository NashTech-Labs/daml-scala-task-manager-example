# DAML-task-manager-scala example

This example demonstrates how to:
- set up and configure Scala codegen (see `codegen` configuration in the `./daml.yaml`)
- instantiate a contract and send a corresponding create command to the ledger
- how to exercise a choice and send a corresponding exercise command
- subscribe to receive ledger events and decode them into generated Scala ADTs

All instructions below assume that you have DAML SDK installed. If you have not installed it yet, please follow these instructions: https://docs.daml.com/getting-started/installation.html

## Create a DAML-task-manager-scala project
```
daml new ./DAML-task-manager-scala DAML-task-manager-scala
```
This should output:
```
Created a new project in "./DAML-task-manager-scala" based on the template "DAML-task-manager-scala".
```
Where:
- `./DAML-task-manager-scala` is a project directory name

## Compile the DAML project
The DAML code for the IOU example is located in the `./daml` folder. Run the following command to build it:
```
$ cd ./DAML-task-manager-scala
$ daml build
```
this should output:
```
Compiling DAML-task-manager-scala to a DAR.
Created .daml/dist/DAML-task-manager-scala-0.0.1.dar.
```

## Generate Scala classes representing DAML contract templates
```
$ daml codegen scala
```
This should generate scala classes in the `./scala-codegen/src/main/scala` directory. See `codegen` configuration in the `daml.yaml` file:
```
...
codegen:
  scala:
    package-prefix: com.knoldus.example.iou.model
    output-directory: scala-codegen/src/main/scala
    verbosity: 2
```

## Start Sandbox
This examples requires a running sandbox. To start it, run the following command:
```
$ daml sandbox ./.daml/dist/DAML-task-manager-scala-0.0.1.dar
```
where `./.daml/dist/DAML-task-manager-scala-0.0.1.dar` is the DAR file created in the previous step.

## Compile and run Scala example
Run the following command from the `DAML-task-manager-scala` folder:
```
$ sbt "application/runMain com.knoldus.example.iou.TaskManagerMain localhost 6865"
```
If example completes successfully, the above process should terminate and the output should look like this:
```
<Sent and received messages>
...
11:54:03.865 [run-main-0] INFO - Shutting down...
...
[success] Total time: 7 s, completed Sep 12, 2019, 11:54:04 AM
```
