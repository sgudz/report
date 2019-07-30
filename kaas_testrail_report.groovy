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

if (! env.PARENT_NODE_NAME) {
    error "'PARENT_NODE_NAME' must be set from the parent deployment job!"
}

currentBuild.description = "${PARENT_NODE_NAME}:${ENV_NAME}"

timeout(time: 2, unit: 'HOURS') {
node ("${PARENT_NODE_NAME}") {
    if (! fileExists("${PARENT_WORKSPACE}")) {
        error "'PARENT_WORKSPACE' contains path to non-existing directory ${PARENT_WORKSPACE} on the node '${PARENT_NODE_NAME}'."
    }
    dir("${PARENT_WORKSPACE}") {
        def description = ''
        def exception_message = ''
        try {

            def report_name = ''
            def testSuiteName = ''
            def methodname = ''
            def testrail_name_template = ''
            def reporter_extra_options = []

            def reports_urls = [:]

            if (deployment_report_name) {
                stage("Deployment report") {
                    testSuiteName = "[MCP] Integration automation"
                    methodname = '{methodname}'
                    testrail_name_template = '{title}'
                    reporter_extra_options = [
                      "--testrail-add-missing-cases",
                      "--testrail-case-custom-fields {\\\"custom_qa_team\\\":\\\"9\\\"}",
                      "--testrail-case-section-name \'All\'",
                    ]
                    ret = shared.upload_results_to_testrail(deployment_report_name, testSuiteName, methodname, testrail_name_template, reporter_extra_options)
                    common.printMsg(ret.stdout, "blue")
                    report_url = ret.stdout.split("\n").each {
                        if (it.contains("[TestRun URL]")) {
                            common.printMsg("Found report URL: " + it.trim().split().last(), "blue")
                            description += "<a href=" + it.trim().split().last() + ">${testSuiteName}</a><br>"
                        }
                    }
                    exception_message += ret.exception
                }
            }

            if (tcpqa_report_name) {
                stage("tcp-qa cases report") {
                    testSuiteName = "[MCP_X] integration cases"
                    methodname = "{methodname}"
                    testrail_name_template = "{title}"
                    reporter_extra_options = [
                      "--testrail-add-missing-cases",
                      "--testrail-case-custom-fields {\\\"custom_qa_team\\\":\\\"9\\\"}",
                      "--testrail-case-section-name \'All\'",
                    ]
                    ret = shared.upload_results_to_testrail(tcpqa_report_name, testSuiteName, methodname, testrail_name_template, reporter_extra_options)
                    common.printMsg(ret.stdout, "blue")
                    report_url = ret.stdout.split("\n").each {
                        if (it.contains("[TestRun URL]")) {
                            common.printMsg("Found report URL: " + it.trim().split().last(), "blue")
                            description += "<a href=" + it.trim().split().last() + ">${testSuiteName}</a><br>"
                        }
                    }
                    exception_message += ret.exception
                }
            }


            // Check if there were any exceptions during reporting
            if (exception_message) {
                throw new Exception(exception_message)
            }

        } catch (e) {
            common.printMsg("Job is failed", "purple")
            throw e
        } finally {
            // reporting is failed for some reason
            writeFile(file: "description.txt", text: description, encoding: "UTF-8")
        }
    }
}
} // timeout
