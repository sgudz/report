def reports_map = ["bootstrap_report": env.BOOTSTRAP_REPORT,
                   "kubeconfig": env.KUBECONFIG, "management_logs": env.MANAGEMENT_LOGS]

node () {
  for (element in reports_map) {
      // String[] file = element.value.split("/")
      // String file_name = file[file.lenght - 1]
      int index = element.value.lastIndexOf('/');
      String file_name = element.value.substring(index +1);
      echo "${file_name}"
      echo "${element.key} ${element.value}"
      run_cmd("wget -O ${workspace}/${file_name} ${element.value}")
  }
}
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
