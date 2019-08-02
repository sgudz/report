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
def runCmd(String cmd, String virtualenv='', Boolean verbose=true, Boolean check_status=true) {
    def common = new com.mirantis.mk.Common()

    def script
    def redirect_output
    def result = [:]
    def stdout_path = sh(script: '#!/bin/bash +x\nmktemp', returnStdout: true).trim()
    def stderr_path = sh(script: '#!/bin/bash +x\nmktemp', returnStdout: true).trim()

    if (verbose) {
        // show stdout to console and store to stdout_path
        redirect_output = " 1> >(tee -a ${stdout_path}) 2>${stderr_path}"
    } else {
        // only store stdout to stdout_path
        redirect_output = " 1>${stdout_path} 2>${stderr_path}"
    }

    if (virtualenv) {
        common.infoMsg("Run shell command in Python virtualenv [${virtualenv}]:\n" + cmd)
        script = """#!/bin/bash +x
            . ${virtualenv}/bin/activate
            ( ${cmd.stripIndent()} ) ${redirect_output}
        """
    } else {
        common.infoMsg('Run shell command:\n' + cmd)
        script = """#!/bin/bash +x
            ( ${cmd.stripIndent()} ) ${redirect_output}
        """
    }

    result['status'] = sh(script: script, returnStatus: true)
    result['stdout'] = readFile(stdout_path)
    result['stderr'] = readFile(stderr_path)
    def cleanup_script = """#!/bin/bash +x
        rm ${stdout_path} || true
        rm ${stderr_path} || true
    """
    sh(script: cleanup_script)

    if (result['status'] != 0 && check_status) {
        def error_message = '\nScript returned exit code: ' + result['status'] + '\n<<<<<< STDERR: >>>>>>\n' + result['stderr']
        common.errorMsg(error_message)
        common.printMsg('', 'reset')
        throw new Exception(error_message)
    }

    if (result['stderr'] && verbose) {
        def warning_message = '\nScript returned exit code: ' + result['status'] + '\n<<<<<< STDERR: >>>>>>\n' + result['stderr']
        common.warningMsg(warning_message)
        common.printMsg('', 'reset')
    }

    return result
}
