/**
 * Report to testrail Pipeline
 * TESTRAIL_CREDENTIALS_ID - Testrail credentails ID
 * REPORT_SI_KAAS_BOOTSTRAP: KaaS bootstrap report
 * REPORT_SI_KAAS_UI: Integration report of KaaS UI test lauch
 * REPORT_KAAS_UI: KaaS UI tests results
 * REPORT_TEMPEST_TESTS: Tempest tests results
 * KAAS_MANAGEMENT_CLUSTER_K8S_VERSION: K8s version of mgmt cluster
 **/

def common = new com.mirantis.mk.Common()
def python = new com.mirantis.mk.Python()
def slaveNode = env.SLAVE_NODE ?: 'python'
def reporting_timeout = env.REPORTING_TIMEOUT ?: 7200
def KAAS_MANAGEMENT_CLUSTER_K8S_VERSION = env.KAAS_MANAGEMENT_CLUSTER_K8S_VERSION ?: ''

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
       'suite': '[MCP2.0] KaaS UI tests',
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
       'suite': "[MCP2.0] $KAAS_MANAGEMENT_CLUSTER_K8S_VERSION K8s Conformance",
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
      if (! "${ENV_NAME}") {
          throw new Exception("ENV_NAME is not set")
      }
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
            common.printMsg("job parameter name: ${param.key}", 'cyan')
            common.printMsg("suite name: ${param.value['suite']}", 'cyan')
            common.printMsg("method name: ${param.value['method']}", 'cyan')
            if ("${param.key}" == "REPORT_K8S_MGMT" && ! KAAS_MANAGEMENT_CLUSTER_K8S_VERSION) {
                common.errorMsg("KAAS_MANAGEMENT_CLUSTER_K8S_VERSION is not set")
                common.errorMsg("K8s Conformance test report for Management cluster will not be uploaded to TestRail")
                return
            }
            if (env[param.key]) {
                //reportName = env[param.key].substring(env[param.key].lastIndexOf('/') + 1)
                reportName = "${param.key}.xml"
                try {
                    //xml_report = python.runCmd("wget ${env[param.key]} -O $workspace/$reportName")
                    xml_report = python.runCmd("wget ${env[param.key]} -O $workspace/${param.key}.xml")
                    python.runCmd("test -s $workspace/${param.key}.xml")
                }
                catch(Exception e) {
                    common.errorMsg("${param.key} report is not available or empty. Skipping.")
                    return
                }
                common.printMsg("Reporting ${param.key}.xml from ${env[param.key]}", "cyan")
                testSuiteName = "${param.value['suite']}"
                methodname = "${param.value['method']}"
                testrailNameTemplate = '{title}'
                reporterExtraOptions = [
                  '--testrail-add-missing-cases',
                  '--testrail-case-custom-fields {\\\"custom_qa_team\\\":\\\"9\\\"}',
                  "--testrail-case-section-name \'All\'",
                  ]
                try {
                    ret = uploadResultsToTestrail(reportName, testSuiteName, methodname, testrailNameTemplate, reporterExtraOptions)
                    common.printMsg(ret.stdout, 'cyan')
                } finally {
                    common.printMsg("Succesfully rported. Removing file ${param.key}.xml", 'cyan')
                    python.runCmd("rm $workspace/${param.key}.xml || true")
                }
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
      def testrail_cred_id = env.TESTRAIL_CREDENTIALS_ID ?: 'system-integration-team-ci'
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
   
   // This is used to use correct username pattern for testrail
      withCredentials([
                 [$class          : 'UsernamePasswordMultiBinding',
                 credentialsId   : testrail_cred_id,
                 passwordVariable: 'TESTRAIL_PASSWORD',
                 usernameVariable: 'TESTRAIL_USER'],
      ]) {
        if ( TESTRAIL_USER.contains('@mirantis.com')) {
            reporterOptions += "--testrail-user \"\${TESTRAIL_USER}\""
        } else {
            reporterOptions += "--testrail-user \"\${TESTRAIL_USER}@mirantis.com\""
        }
      } // withCredentials
   
   
      def script = "report ${reporterOptions.join(' ')} '${workspace}/${reportName}'"
      withCredentials([
                 [$class          : 'UsernamePasswordMultiBinding',
                 credentialsId   : testrail_cred_id,
                 passwordVariable: 'TESTRAIL_PASSWORD',
                 usernameVariable: 'TESTRAIL_USER'],
      ]) {
        python.runCmd(script, venvPath, true, false)
      }
}
