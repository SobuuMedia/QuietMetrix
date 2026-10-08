tasks.register<Exec>("e2eTest") {
    description = "Runs mandatory HTTP contract checks against explicitly configured disposable backends"
    group = "verification"
    workingDir = file(".")
    commandLine("bash", "run-e2e.sh")
}
