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
def report_filename = env.REPORT_SI_KAAS_BOOTSTRAP
println ${report_filename}

node () {
    stage("tcp-qa cases report") {
        testSuiteName = "[MCP_X] integration cases"
        methodname = "{methodname}"
        testrail_name_template = "{title}"
        reporter_extra_options = [
        "--testrail-add-missing-cases",
        "--testrail-case-custom-fields {\\\"custom_qa_team\\\":\\\"9\\\"}",
        "--testrail-case-section-name \'All\'",
        ]
        ret = upload_results_to_testrail(report_filename, testSuiteName, methodname, testrail_name_template, reporter_extra_options)
        common.printMsg(ret.stdout, "blue")
        report_url = ret.stdout.split("\n").each {
        if (it.contains("[TestRun URL]")) {
            common.printMsg("Found report URL: " + it.trim().split().last(), "blue")
            description += "<a href=" + it.trim().split().last() + ">${testSuiteName}</a><br>"
            }
        }
    } // stage
} // node
def upload_results_to_testrail(report_name, testSuiteName, methodname, testrail_name_template, reporter_extra_options=[]) {
      def venvPath = '/home/jenkins/venv_testrail_reporter'
      def testrailURL = "https://mirantis.testrail.com"
      def testrailProject = "Mirantis Cloud Platform"
      def testPlanNamePrefix = env.TEST_PLAN_NAME_PREFIX ?: "[2019.2.0-update]System"
      def testPlanName = "${testPlanNamePrefix}-${ENV_NAME}-${new Date().format('yyyy-MM-dd')}"
      def testPlanDesc = env.ENV_NAME
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
        wget -O ${report_filename} $venvPath
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
            ret.stdout = sh(script: script, returnStdout: true)
        } catch (Exception ex) {
            ret.exception = ("""\
    ##### Report to '${
                             }' failed: #####\n""" + ex.message + "\n\n")
        }
        return ret
      }
}
