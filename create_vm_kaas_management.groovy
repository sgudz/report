// PARAM: export ENV_NAME=test-01

// PARAM:    # Variables used to render KaaS bootstrap templates
// PARAM:    export KAAS_VERSION=0.2.11
// PARAM:    export KAAS_EXTERNAL_NETWORK_ID=bf6b85a1-39db-4582-b0d1-f4291dddb9cf
// PARAM:    export KAAS_MANAGEMENT_CLUSTER_DNS1=172.18.224.4
// PARAM:    export KAAS_MANAGEMENT_CLUSTER_FLAVOR=kaas.small
// PARAM:    export KAAS_MANAGEMENT_CLUSTER_IMAGE=bionic-server-cloudimg-amd64-20190612
// PARAM:    export KAAS_MANAGEMENT_CLUSTER_K8S_VERSION=1.13.5
// PARAM:    export KAAS_MANAGEMENT_CLUSTER_SLAVE_NODES=1

// PARAM: export OPENSTACK_API_CREDENTIALS=system-integration-team-ci  =>
                                                                     //    # Variables used to render clouds.yaml
                                                                     //    export OS_USERNAME=...
                                                                     //    export OS_PASSWORD=...
// PARAM:    export OS_AUTH_URL=https://ic-eu.ssl.mirantis.net:5000/v3
// PARAM:    export OS_IDENTITY_API_VERSION=3
// PARAM:    export OS_INTERFACE=public
// PARAM:    export OS_PROJECT_ID=7fd2d1904af849ccb2d72fcfb8469c97
// PARAM:    export OS_PROJECT_NAME=systest-team
///////////////////////////////////////// not implemented in si-tests : export OS_PROJECT_DOMAIN_ID=default
// PARAM:    export OS_REGION_NAME=RegionOne
// PARAM:    export OS_USER_DOMAIN_NAME=default

// PARAM: export GERRIT_MCP_CREDENTIALS_ID=f4fb9dd6-ba63-4085-82f7-3fa601334d95  # private key for mcp-gerrit to clone si-tests
// PARAM: export CLOUD_KEY_NAME=kaas-vm-seed-node   # the same as 8133
// PARAM: export SEED_PRIVATE_KEY_CREDENTIAL_ID=02177449-43b5-41d7-b31f-0ab77acedb71  =>  export SEED_SSH_PRIV_KEY_FILE=~/.ssh/id_rsa_system_key_8133

// PARAM: export SI_TESTS_REFSPEC=
// PARAM: export SI_TESTS_BRANCH=
// PARAM: export BOOTSTRAP_TIMEOUT=3600

// PARAM: export RUN_TESTS=si_tests/tests/deployment/management-cluster/test_provision_mgm_cluster.py  # space separated list of tests to bootstrap kaas

def gerrit = new com.mirantis.mk.Gerrit()
def report_filename = "./artifacts/bootstrap_kaas_result.xml"

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

timeout(time: env.BOOTSTRAP_TIMEOUT.toInteger(), unit: 'SECONDS') {
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

        try {
            stage('Run bootstrap kaas on vm test') {
                withCredentials([[$class: 'SSHUserPrivateKeyBinding',
                                  credentialsId: env.SEED_PRIVATE_KEY_CREDENTIAL_ID,
                                  keyFileVariable: "SEED_SSH_PRIV_KEY_FILE",
                                  usernameVariable: "SEED_SSH_PRIV_KEY_USERNAME",
                                  passwordVariable: "SEED_SSH_PRIV_KEY_PASSWORD"]]) {

                    withCredentials([
                               [$class          : 'UsernamePasswordMultiBinding',
                               credentialsId   : env.OPENSTACK_API_CREDENTIALS,
                               passwordVariable: 'OS_PASSWORD',
                               usernameVariable: 'OS_USERNAME']
                    ]) {
                            // Run bootstrap test
                            run_cmd("py.test --junit-xml=${report_filename} ${env.RUN_TESTS}")
                            // run_cmd("ls -l > ${report_filename}")
                    }
                }
            } // stage
        } finally {
            stage("Archive xml report and bootstrap artifacts") {
                archiveArtifacts artifacts: "artifacts/*"
            } // stage
        } // try
    } // node
} // timeout
