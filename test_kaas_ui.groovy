// PARAM: KUBECONFIG_ARTIFACT

// PARAM: export GERRIT_MCP_CREDENTIALS_ID=f4fb9dd6-ba63-4085-82f7-3fa601334d95  # private key for mcp-gerrit to clone si-tests
// PARAM: export SI_TESTS_REFSPEC=
// PARAM: export SI_TESTS_BRANCH=
// PARAM: export TEST_KAAS_UI_TIMEOUT=3600

// PARAM: export RUN_TESTS=si_tests/tests/deployment/management-cluster/test_kaas_ui.py  # space separated list of tests to bootstrap kaas

def gerrit = new com.mirantis.mk.Gerrit()
def report_filename = "./artifacts/test_kaas_ui_result.xml"

// move cmd_run() to pipeline-library
def run_cmd(String cmd, Boolean returnStdout=false) {
    def common = new com.mirantis.mk.Common()
    common.printMsg("Run shell command:\n" + cmd, "blue")
    def VENV_PATH='.venv-si-tests'
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

timeout(time: env.TEST_KAAS_UI_TIMEOUT.toInteger(), unit: 'SECONDS') {
    node () {
        def repo_url
        withCredentials([[$class: 'SSHUserPrivateKeyBinding',
                          credentialsId: env.GERRIT_MCP_CREDENTIALS_ID,
                          keyFileVariable: "GERRIT_KEY",
                          usernameVariable: "GERRIT_USERNAME",
                          passwordVariable: "GERRIT_PASSWORD"]]) {

            def GERRIT_SCHEME = 'ssh'
            def GERRIT_HOST = 'gerrit.mcp.mirantis.com'
            def GERRIT_PORT = '29418'
            def GERRIT_PROJECT = 'kaas/si-tests'
            repo_url = "${GERRIT_SCHEME}://${GERRIT_USERNAME}@${GERRIT_HOST}:${GERRIT_PORT}/${GERRIT_PROJECT}"
        }

        stage('Checkout kaas/si-tests and install requirements') {
            deleteDir()  // TODO(ddmitriev): do more selective cleanup to keep the git repo and virtual env
            gerrit.gerritPatchsetCheckout(repo_url,
                                          env.SI_TESTS_REFSPEC,
                                          env.SI_TESTS_BRANCH,
                                          env.GERRIT_MCP_CREDENTIALS_ID)
            sh ('virtualenv --python=python3.5 ./.venv-si-tests')
            run_cmd('mkdir ./artifacts/')
            run_cmd('pip install -q -r ./si_tests/requirements.txt')
        }

        stage('Download kubeconfig') {
            run_cmd('wget ${KUBECONFIG_ARTIFACT} -O ./kubeconfig')
        } // stage

        try {
            stage('Run kaas ui test') {
                // Run bootstrap test
                run_cmd("""\
export KUBECONFIG=./kubeconfig
export KAAS_UI_TEST_DEST_REPORT=./artifacts/kaas_ui_report.tar
py.test --junit-xml=${report_filename} ${env.RUN_TESTS}
""")
            } // stage
        } finally {
            stage("Archive xml report and bootstrap artifacts") {
                archiveArtifacts artifacts: "artifacts/*"
            } // stage
        } // try
    } // node
} // timeout
