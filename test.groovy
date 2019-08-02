def common = new com.mirantis.mk.Common()
def reports_map = [
   'REPORT_SI_KAAS_BOOTSTRAP': [
       'suite': '[MCP2.0]Integration automation',
   ],
   'REPORT_SI_KAAS_UI': [
       'suite': '[MCP2.0]Integration automation',
   ],
   'REPORT_KAAS_UI': [
       'suite': 'Kaas UI tests',
   ],
]

node () {
  def description = ''
  def workspace = common.getWorkspace()
  def venvPath = "$workspace/testrail-venv"
  def testrailReporterPackage = 'git+https://github.com/dis-xcom/testrail_reporter'
  
  // Install testrail reporter to workspace
  sh """
        virtualenv ${venvPath}
        . ${venvPath}/bin/activate
        pip install --upgrade ${testrailReporterPackage}
      """
  // Download reports to workspace
  stage ("Download reports") {
      reports_map.each { param ->
        println "job parameter name: ${param.key}"
        println "suite name: ${param.value['suite']}"

        if (env[param.key]) {
            file_name = env[param.key].substring(env[param.key].lastIndexOf('/') +1)
            xml_report = run_cmd("wget ${env[param.key]} -O $workspace/$file_name")
            println "xml_report: ${xml_report}"
        } else {
            println "Job parameter ${param.key} is not found or empty. Skipping report"
        }
    } // iterate map
  } //stage

  // Report to testrail
  stage ("Report to testrail") {
      reports_map.each { param ->
        if (env[param.key]) {
          report_name = env[param.key].substring(env[param.key].lastIndexOf('/') +1)
          println "Reporting ${report_name}"
          testSuiteName = "${param.value['suite']}"
          methodname = "{methodname}"
          testrail_name_template = "{title}"
          reporter_extra_options = [
            "--testrail-add-missing-cases",
            "--testrail-case-custom-fields {\\\"custom_qa_team\\\":\\\"9\\\"}",
            "--testrail-case-section-name \'All\'",
            ]
          ret = upload_results_to_testrail(report_name, testSuiteName, methodname, testrail_name_template, reporter_extra_options)
          common.printMsg(ret.stdout, "blue")
          report_url = ret.stdout.split("\n").each {
            if (it.contains("[TestRun URL]")) {
              common.printMsg("Found report URL: " + it.trim().split().last(), "blue")
              description += "<a href=" + it.trim().split().last() + ">${testSuiteName}</a><br>"
            }
          } // report url
        } // if val
      } // iterate map
  } //stage
} //node

def run_cmd(String cmd, Boolean returnStdout=false) {
    def common = new com.mirantis.mk.Common()
    common.printMsg("Run shell command:\n" + cmd, "blue")
    def VENV_PATH='/home/jenkins/fuel-devops30'
    def stderr_path = "/tmp/${JOB_NAME}_${BUILD_NUMBER}_stderr.log"
    def script = """#!/bin/bash
        set +x
        echo 'activate python virtualenv ${VENV_PATH}'
        . ${VENV_PATH}/bin/activate
        bash -c -e -x '${cmd.stripIndent()}' 2>${stderr_path}
    """
    try {
        def stdout = sh(script: script, returnStdout: returnStdout)
        def stderr = readFile("${stderr_path}")
        def error_message = "\n<<<<<< STDERR: >>>>>>\n" + stderr
        common.printMsg(error_message, "yellow")
        common.printMsg("", "reset")
        return stdout
    } catch (e) {
        def stderr = readFile("${stderr_path}")
        def error_message = e.message + "\n<<<<<< STDERR: >>>>>>\n" + stderr
        common.printMsg(error_message, "red")
        common.printMsg("", "reset")
        throw new Exception(error_message)
    } finally {
        sh(script: "rm ${stderr_path} || true")
    }
}

def run_cmd_stdout(cmd) {
    return run_cmd(cmd, true)
}

def upload_results_to_testrail(report_name, testSuiteName, methodname, testrail_name_template, reporter_extra_options=[]) {
      def venvPath = "$workspace/testrail-venv"
      def testrailURL = "https://mirantis.testrail.com"
      def testrailProject = "Mirantis Cloud Platform"
      def testPlanNamePrefix = env.TEST_PLAN_NAME_PREFIX ?: "[MCP2.0]System"
      def testPlanName = "${testPlanNamePrefix}-${new Date().format('yyyy-MM-dd')}"
      def testPlanDesc = env.ENV_NAME
      def testrailMilestone = "MCP2.0"
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
        report ${reporterOptions.join(' ')} '${workspace}/${report_name}'
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
            ##### Report to failed: #####\n""" + ex.message + "\n\n")
        }
        return ret
      }
}
