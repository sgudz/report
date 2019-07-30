scenario_yaml = """\
workflow:
- job: test_sg_report
  ignore_failed: false
  parameters:
    ENV_NAME:
      type: StringParameterValue
      use_variable: ENV_NAME
    KAAS_VERSION:
      type: StringParameterValue
      use_variable: KAAS_VERSION
    SI_TESTS_REFSPEC:
      type: StringParameterValue
      use_variable: SI_TESTS_REFSPEC
  artifacts:
    KUBECONFIG_ARTIFACT: artifacts/management_kubeconfig
    REPORT_SI_KAAS_BOOTSTRAP: artifacts/bootstrap_kaas_result.xml

finnaly:
- job: kaas_testrail_report
  ignore_failed: true
  parameters:
    KUBECONFIG_ARTIFACT:
      type: StringParameterValue
      use_variable: KUBECONFIG_ARTIFACT
    REPORT_SI_KAAS_BOOTSTRAP:
      type: StringParameterValue
      use_variable: REPORT_SI_KAAS_BOOTSTRAP
  artifacts:
    A1: example.yaml
    A2: example222.yaml
"""

def scenario = readYaml text: scenario_yaml
def total_timeout = env.TOTAL_TIMEOUT ?: 3600

runScenario(scenario, total_timeout.toInteger())



def runJob(step, global_variables) {
    def job_name = step['job']
    def job_parameters = []

    // Collect required job_parameters from 'global_variables' or 'env'
    for (param in step['parameters']) {
        println "param is ${param.key} and ${param.value}"
        if (!global_variables[param.value.use_variable]) {
            global_variables[param.value.use_variable] = env[param.value.use_variable] ?: ''
        }
        job_parameters.add([$class: "${param.value.type}", name: "${param.key}", value: global_variables[param.value.use_variable]])
    }

    // Build the job
    def job_info = build job: "${job_name}", parameters: job_parameters, propagate: false
    return job_info
}

def storeArtifacts(build_url, step, global_variables) {
    def http = new com.mirantis.mk.Http()
    def base = [:]
    base["url"] = build_url
    def job_config = http.restGet(base, "/api/json/")
    def job_artifacts = job_config['artifacts']
    for (artifact in step['artifacts']) {
        def job_artifact = job_artifacts.findAll { item -> artifact.value == item['fileName'] || artifact.value == item['relativePath'] }
        if (job_artifact.size() == 1) {
            // Store artifact URL
            def artifact_url = "${build_url}artifact/${job_artifact[0]['relativePath']}"
            global_variables[artifact.key] = artifact_url
            println "Artifact URL ${artifact_url} stored to ${artifact.key}"
        } else if (job_artifact.size() > 1) {
            // Error: too many artifacts with the same name
            println "Multiple artifacts ${artifact.value} for ${artifact.key} found in the build results ${build_url}, expected one:\n${job_artifact}"
        } else {
            // Error: no artifact with expected name
            println "Artifact ${artifact.value} for ${artifact.key} not found in the build results ${build_url}, found the following artifacts:\n${job_artifacts}"
        }
    }
}

def runSteps(steps, global_variables, failed_jobs) {
    for (step in steps) {
        stage("Running job ${step['job']}") {

            // Collect job parameters and run the job
            def job_info = runJob(step, global_variables)
            def job_result = job_info.getResult()
            def build_url = job_info.getAbsoluteUrl()

            // Check the job result
            if (job_result == "SUCCESS") {
                // Store links to the resulting artifacts into 'global_variables'
                storeArtifacts(build_url, step, global_variables)
            } else {
                // Job failed, fail the build or keep going depending on 'ignore_failed' flag
                def job_ignore_failed = step['ignore_failed'] ?: false
                failed_jobs[build_url] = job_result
                if (job_ignore_failed) {
                    println "Job ${build_url} finished with result: ${job_result}"
                } else {
                    currentBuild.result = job_result
                    throw new Exception("Job ${build_url} finished with result: ${job_result}")
                }
            } // if (job_result == "SUCCESS")
        } // stage ("Running job ${step['job']}")
    } // for (step in scenario['workflow'])
}

def runScenario(scenario, total_timeout) {

    timeout(time: total_timeout, unit: 'SECONDS') {

        // Collect the parameters for the jobs here
        global_variables = [:]
        // List of failed jobs to show at the end
        failed_jobs = [:]

        try {
            // Run the 'workflow' jobs
            runSteps(scenario['workflow'], global_variables, failed_jobs)

        } catch (InterruptedException x) {
            error "The job was aborted"

        } catch (e) {
            error("Build failed: " + e.toString())

        } finally {
            // Run the 'finnaly' jobs
            runSteps(scenario['finnaly'], global_variables, failed_jobs)

            if (failed_jobs) {
                println "Failed jobs: ${failed_jobs}"
                currentBuild.result = "FAILED"
            }
        } // try

    } // timeout
}
