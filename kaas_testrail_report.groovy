/**
 *
 *
 * Expected parameters:
 *   ENV_NAME                      Fuel-devops environment name
 *   PARENT_NODE_NAME              Name of the jenkins slave to create the environment
 *   PARENT_WORKSPACE              Path to the workspace of the parent job to use tcp-qa repo
 *   TEMPEST_TEST_SUITE_NAME       Name of tempest suite
 */

def common = new com.mirantis.mk.Common()

if (! env.PARENT_NODE_NAME) {
    error "'PARENT_NODE_NAME' must be set from the parent deployment job!"
}

currentBuild.description = "${PARENT_NODE_NAME}:${ENV_NAME}"

timeout(time: 2, unit: 'HOURS') {
node ("${PARENT_NODE_NAME}") {
    if (! fileExists("${PARENT_WORKSPACE}")) {
        error "'PARENT_WORKSPACE' contains path to non-existing directory ${PARENT_WORKSPACE} on the node '${PARENT_NODE_NAME}'."
    }
    dir("${PARENT_WORKSPACE}") {
        def description = ''
        def exception_message = ''
        try {

            def report_name = ''
            def testSuiteName = ''
            def methodname = ''
            def testrail_name_template = ''
            def reporter_extra_options = []

            def reports_urls = [deployment_report_name:REPORT_SI_KAAS_BOOTSTRAP]

            if (deployment_report_name) {
                stage("Deployment report") {
                    testSuiteName = "[MCP] Integration automation"
                    methodname = '{methodname}'
                    testrail_name_template = '{title}'
                    reporter_extra_options = [
                      "--testrail-add-missing-cases",
                      "--testrail-case-custom-fields {\\\"custom_qa_team\\\":\\\"9\\\"}",
                      "--testrail-case-section-name \'All\'",
                    ]
                    ret = upload_results_to_testrail(deployment_report_name, testSuiteName, methodname, testrail_name_template, reporter_extra_options)
                    common.printMsg(ret.stdout, "blue")
                    report_url = ret.stdout.split("\n").each {
                        if (it.contains("[TestRun URL]")) {
                            common.printMsg("Found report URL: " + it.trim().split().last(), "blue")
                            description += "<a href=" + it.trim().split().last() + ">${testSuiteName}</a><br>"
                        }
                    }
                    exception_message += ret.exception
                }
            }

            if (tcpqa_report_name) {
                stage("tcp-qa cases report") {
                    testSuiteName = "[MCP_X] integration cases"
                    methodname = "{methodname}"
                    testrail_name_template = "{title}"
                    reporter_extra_options = [
                      "--testrail-add-missing-cases",
                      "--testrail-case-custom-fields {\\\"custom_qa_team\\\":\\\"9\\\"}",
                      "--testrail-case-section-name \'All\'",
                    ]
                    ret = upload_results_to_testrail(tcpqa_report_name, testSuiteName, methodname, testrail_name_template, reporter_extra_options)
                    common.printMsg(ret.stdout, "blue")
                    report_url = ret.stdout.split("\n").each {
                        if (it.contains("[TestRun URL]")) {
                            common.printMsg("Found report URL: " + it.trim().split().last(), "blue")
                            description += "<a href=" + it.trim().split().last() + ">${testSuiteName}</a><br>"
                        }
                    }
                    exception_message += ret.exception
                }
            }


            // Check if there were any exceptions during reporting
            if (exception_message) {
                throw new Exception(exception_message)
            }

        } catch (e) {
            common.printMsg("Job is failed", "purple")
            throw e
        } finally {
            // reporting is failed for some reason
            writeFile(file: "description.txt", text: description, encoding: "UTF-8")
        }
    }
}
} // timeout

def swarm_testrail_report(String passed_steps, String node_with_reports) {
        // Run pytest tests
        def common = new com.mirantis.mk.Common()
        def tcp_qa_refs = env.TCP_QA_REFS ?: ''
        def tempest_test_suite_name = env.TEMPEST_TEST_SUITE_NAME
        def test_plan_name_prefix = env.TEST_PLAN_NAME_PREFIX ?: ''
        def parameters = [
                string(name: 'ENV_NAME', value: "${ENV_NAME}"),
                string(name: 'LAB_CONFIG_NAME', value: "${LAB_CONFIG_NAME}"),
                string(name: 'MCP_VERSION', value: "${MCP_VERSION}"),
                string(name: 'PASSED_STEPS', value: passed_steps),
                string(name: 'PARENT_NODE_NAME', value: node_with_reports),
                string(name: 'PARENT_WORKSPACE', value: pwd()),
                string(name: 'TCP_QA_REFS', value: "${tcp_qa_refs}"),
                string(name: 'TEMPEST_TEST_SUITE_NAME', value: "${tempest_test_suite_name}"),
                string(name: 'TEST_PLAN_NAME_PREFIX', value: "${test_plan_name_prefix}"),
            ]
        common.printMsg("Start building job 'swarm-testrail-report' with parameters:", "purple")
        common.prettyPrint(parameters)
        build job: 'swarm-testrail-report',
            parameters: parameters
}

def upload_results_to_testrail(report_name, testSuiteName, methodname, testrail_name_template, reporter_extra_options=[]) {
  def venvPath = '/home/jenkins/venv_testrail_reporter'
  def testPlanDesc = env.LAB_CONFIG_NAME
  def testrailURL = "https://mirantis.testrail.com"
  def testrailProject = "Mirantis Cloud Platform"
  def testPlanNamePrefix = env.TEST_PLAN_NAME_PREFIX ?: "[2019.2.0-update]System"
  def testPlanName = "${testPlanNamePrefix}-${MCP_VERSION}-${new Date().format('yyyy-MM-dd')}"
  def testrailMilestone = "MCP1.1"
  def testrailCaseMaxNameLenght = 250
  def jobURL = env.BUILD_URL

  def reporterOptions = [
    "--verbose",
    "--env-description \"${testPlanDesc}\"",
    "--testrail-run-update",
    "--testrail-url \"${testrailURL}\"",
    "--testrail-user \"\${TESTRAIL_USER}\"",
    "--testrail-password \"\${TESTRAIL_PASSWORD}\"",
    "--testrail-project \"${testrailProject}\"",
    "--testrail-plan-name \"${testPlanName}\"",
    "--testrail-milestone \"${testrailMilestone}\"",
    "--testrail-suite \"${testSuiteName}\"",
    "--xunit-name-template \"${methodname}\"",
    "--testrail-name-template \"${testrail_name_template}\"",
    "--test-results-link \"${jobURL}\"",
    "--testrail-case-max-name-lenght ${testrailCaseMaxNameLenght}",
  ] + reporter_extra_options

  def script = """
    . ${venvPath}/bin/activate
    set -ex
    report ${reporterOptions.join(' ')} '${report_name}'
  """

  def testrail_cred_id = params.TESTRAIL_CRED ?: 'testrail_system_tests'

  withCredentials([
             [$class          : 'UsernamePasswordMultiBinding',
             credentialsId   : testrail_cred_id,
             passwordVariable: 'TESTRAIL_PASSWORD',
             usernameVariable: 'TESTRAIL_USER']
  ]) {
    def ret = [:]
    ret.stdout = ''
    ret.exception = ''
    try {
        ret.stdout = run_cmd_stdout(script)
    } catch (Exception ex) {
        ret.exception = ("""\
##### Report to '${testSuiteName}' failed: #####\n""" + ex.message + "\n\n")
    }
    return ret
  }
}
