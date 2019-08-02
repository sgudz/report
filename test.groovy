def common = new com.mirantis.mk.Common()
def reports_map = [
   'REPORT_SI_KAAS_BOOTSTRAP': [
       'suite': '[MCP2.0]Integration automation',
       ....
   ],
   'REPORT_SI_KAAS_UI': [
       'suite': '[MCP2.0]Integration automation',
       ....
   ],
   'REPORT_KAAS_UI': [
       'suite': 'Kaas UI tests',
       ....
   ],
]

node () {
  def description = ''
  def workspace = common.getWorkspace()
  def venvPath = "$workspace/testrail-venv"
  def testrailReporterPackage = 'git+https://github.com/dis-xcom/testrail_reporter'
  for (param in reports_map) {
        println "job parameter name: ${param.key}"
        println "suite name: ${param.value['suite']}"

        if (env[param.key]) {
            xml_report = runCmd("wget ${env[param.key]} -O ")
          println "xml_report: ${xml_report}"


        } else {
            println "Job parameter ${param.key} is not found or empty"
        }

    }
  
  } 
