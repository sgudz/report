/**
 * Report to testrail Pipeline
 * TESTRAIL_CREDENTIALS_ID - Testrail credentails ID
 * REPORT_SI_KAAS_BOOTSTRAP: KaaS bootstrap report
 * REPORT_SI_KAAS_UI: Integration report of KaaS UI test lauch
 * REPORT_KAAS_UI: KaaS UI tests results
 * REPORT_TEMPEST_TESTS: Tempest tests results
 **/

def common = new com.mirantis.mk.Common()
def python = new com.mirantis.mk.Python()
def slaveNode = env.SLAVE_NODE ?: 'python'
def reporting_timeout = env.REPORTING_TIMEOUT ?: 7200

def reports_map = [
   'REPORT_SI_KAAS_BOOTSTRAP': [
       'suite': '[MCP2.0]Integration automation',
       'method': '{methodname}',
       'desc': 'KaaS bootstrap report'
   ],
   'REPORT_SI_KAAS_UI': [
       'suite': '[MCP2.0]Integration automation',
       'method': '{methodname}',
       'desc': 'Integration report of KaaS UI test lauch'
   ],
   'REPORT_KAAS_UI': [
       'suite': 'Kaas UI tests',
       'method': '{methodname}',
       'desc': ' KaaS UI tests results'
   ],
   'REPORT_SI_IAM': [
       'suite': '[MCP2.0]Integration automation',
       'method': '{methodname}',
       'desc': 'Integration report of IAM bdd test lauch'
   ],
   'REPORT_SI_K8S_MGMT': [
       'suite': '[MCP2.0]Integration automation',
       'method': '{methodname}',
       'desc': 'Integration report of K8S Conformance mgmt cluster test lauch'
   ],
   'REPORT_K8S_MGMT': [
       'suite': 'K8S Conformance mgmt cluster tests',
       'method': '{methodname}',
       'desc': 'K8S Conformance mgmt cluster UI tests results'
   ],
   'REPORT_TEMPEST_TESTS': [
       'suite': 'Tempest 16.0.0 base',
       'method': '{classname}.{methodname}',
       'desc': 'Tempest tests results'
   ],
]
timeout(time: reporting_timeout.toInteger(), unit: 'SECONDS') {
    node (slaveNode) {
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
      stage ('Download reports and report to testrail') {
          reports_map.each { param ->
            common.printMsg("job parameter name: ${param.key}", 'blue')
            common.printMsg("suite name: ${param.value['suite']}", 'blue')
            common.printMsg("method name: ${param.value['method']}", 'blue')
            if (env[param.key]) {
                reportName = env[param.key].substring(env[param.key].lastIndexOf('/') + 1)
                xml_report = python.runCmd("wget ${env[param.key]} -O $workspace/$reportName")
                println "Reporting ${reportName}"
                testSuiteName = "${param.value['suite']}"
                methodname = "${param.value['method']}"
                testrailNameTemplate = '{title}'
                reporterExtraOptions = [
                  '--testrail-add-missing-cases',
                  '--testrail-case-custom-fields {\\\"custom_qa_team\\\":\\\"9\\\"}',
                  "--testrail-case-section-name \'All\'",
                  ]
                ret = uploadResultsToTestrail(reportName, testSuiteName, methodname, testrailNameTemplate, reporterExtraOptions)
                common.printMsg(ret.stdout, 'blue')
                //report_url = ret.stdout.split('\n').each {
                  //if (it.contains('[TestRun URL]')) {
                  //  common.printMsg('Found report URL: ' + it.trim().split().last(), 'blue')
                  //  description += '<a href=' + it.trim().split().last() + ">${testSuiteName}</a><br>"
                  //}
                //} // report url
            //} else {
                //println "Job parameter ${param.key} is not found or empty. Skipping report"
            }
        } // iterate map
      } //stage
    } // node
} //timeout

def uploadResultsToTestrail(reportName, testSuiteName, methodname, testrailNameTemplate, reporterExtraOptions=[]) {
      def python = new com.mirantis.mk.Python()
      def venvPath = "$workspace/testrail-venv"
      def testrailURL = 'https://mirantis.testrail.com'
      def testrailProject = 'Mirantis Cloud Platform'
      def testPlanNamePrefix = env.TEST_PLAN_NAME_PREFIX ?: '[MCP2.0]System'
      def testPlanName = "${testPlanNamePrefix}-${new Date().format('yyyy-MM-dd')}"
      def testPlanDesc = "${ENV_NAME}-${KAAS_VERSION}"
      def testrailMilestone = 'MCP2.0'
      def testrailCaseMaxNameLenght = 250
      def jobURL = env.BUILD_URL
      def reporterOptions = [
        '--verbose',
        "--env-description \"${testPlanDesc}\"",
        '--testrail-run-update',
        "--testrail-url \"${testrailURL}\"",
        "--testrail-password \"\${TESTRAIL_PASSWORD}\"",
        "--testrail-project \"${testrailProject}\"",
        "--testrail-plan-name \"${testPlanName}\"",
        "--testrail-milestone \"${testrailMilestone}\"",
        "--testrail-suite \"${testSuiteName}\"",
        "--xunit-name-template \"${methodname}\"",
        "--testrail-name-template \"${testrailNameTemplate}\"",
        "--test-results-link \"${jobURL}\"",
        "--testrail-case-max-name-lenght ${testrailCaseMaxNameLenght}",
      ] + reporterExtraOptions

      def script = "report ${reporterOptions.join(' ')} '${workspace}/${reportName}'"
      def testrail_cred_id = env.TESTRAIL_CREDENTIALS_ID ?: 'system-integration-team-ci'

      withCredentials([
                 [$class          : 'UsernamePasswordMultiBinding',
                 credentialsId   : testrail_cred_id,
                 passwordVariable: 'TESTRAIL_PASSWORD',
                 usernameVariable: 'TESTRAIL_USER'],
      ]) {
        if (! TESTRAIL_USER.contains('@mirantis.com')) {
            reporterOptions += "--testrail-user \"\${TESTRAIL_USER}@mirantis.com\""
        } else {
            reporterOptions += "--testrail-user \"\${TESTRAIL_USER}\""
        }
        def ret = [:]
        ret.stdout = ''
        ret.exception = ''
        try {
            ret.stdout = python.runCmd(script, venvPath)
        } catch (Exception ex) {
            ret.exception = ("""\
            ##### Report to failed: #####\n""" + ex.message + '\n\n')
        }
        return ret
      }
}
