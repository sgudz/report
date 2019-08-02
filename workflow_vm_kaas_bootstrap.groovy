scenario_yaml = """\
workflow:
- job: test_sg_report
  ignore_failed: false
  parameters:
    ENV_NAME:
      type: StringParameterValue
      use_variable: ENV_NAME
    KAAS_VERSION:
      type: StringParameterValue
      use_variable: KAAS_VERSION
    SI_TESTS_REFSPEC:
      type: StringParameterValue
      use_variable: SI_TESTS_REFSPEC
  artifacts:
    KUBECONFIG_ARTIFACT: artifacts/management_kubeconfig
    REPORT_SI_KAAS_BOOTSTRAP: artifacts/bootstrap_kaas_result.xml

finally:
- job: kaas_testrail_report
  ignore_failed: true
  parameters:
    KUBECONFIG_ARTIFACT:
      type: StringParameterValue
      use_variable: KUBECONFIG_ARTIFACT
    REPORT_SI_KAAS_BOOTSTRAP:
      type: StringParameterValue
      use_variable: REPORT_SI_KAAS_BOOTSTRAP
    ENV_NAME:
      type: StringParameterValue
      use_variable: ENV_NAME
    KAAS_VERSION:
      type: StringParameterValue
      use_variable: KAAS_VERSION
  artifacts:
    A1: example.yaml
    A2: example222.yaml
"""

def workflow = new com.mirantis.mk.Workflow()
def scenario = readYaml text: scenario_yaml
def total_timeout = env.TOTAL_TIMEOUT ?: 3600

timeout(time: total_timeout.toInteger(), unit: 'SECONDS') {
    workflow.runScenario(scenario)
} // timeout
