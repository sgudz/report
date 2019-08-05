def common = new com.mirantis.mk.Common()
def python = new com.mirantis.mk.Python()
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
        if ! [ -d ${venvPath} ]; then
          virtualenv ${venvPath}
        fi
        . ${venvPath}/bin/activate
        pip install --upgrade ${testrailReporterPackage}
      """
  // Download reports to workspace
  stage ("Download reports and report to testrail") {
      reports_map.each { param ->
        println "job parameter name: ${param.key}"
        println "suite name: ${param.value['suite']}"
        if (env[param.key]) {
            report_name = env[param.key].substring(env[param.key].lastIndexOf('/') +1)
            xml_report = python.runCmd("wget ${env[param.key]} -O $workspace/$report_name")
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
        } else {
            println "Job parameter ${param.key} is not found or empty. Skipping report"
        }
    } // iterate map
  } //stage
}

def upload_results_to_testrail(report_name, testSuiteName, methodname, testrail_name_template, reporter_extra_options=[]) {
      def common = new com.mirantis.mk.Common()
      def python = new com.mirantis.mk.Python()
      def venvPath = "$workspace/testrail-venv"
      common.printMsg("venvPath: $venvPath", "blue")
      def testrailURL = "https://mirantis.testrail.com"
      def testrailProject = "Mirantis Cloud Platform"
      def testPlanNamePrefix = env.TEST_PLAN_NAME_PREFIX ?: "[MCP2.0]System"
      def testPlanName = "${testPlanNamePrefix}-${new Date().format('yyyy-MM-dd')}"
      def testPlanDesc = "${ENV_NAME}-${KAAS_VERSION}"
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

      def script = "report ${reporterOptions.join(' ')} '${workspace}/${report_name}'"
      common.printMsg("Script: $script", "blue")  
      def testrail_cred_id = env.TESTRAIL_CREDENTIALS_ID ?: 'system-integration-team-ci'

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
            ret.stdout = python.runCmd(script, venvPath)
        } catch (Exception ex) {
            ret.exception = ("""\
            ##### Report to failed: #####\n""" + ex.message + "\n\n")
        }
        return ret
      }
}
