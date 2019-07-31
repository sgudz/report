def reports_map = ["bootstrap_report": env.BOOTSTRAP_REPORT,
                   "kubeconfig": env.KUBECONFIG, "env_name": env.ENV_NAME,
                   "kaas_version": env.KAAS_VERSION]

for (element in map) {
    echo "${element.key} ${element.value}"
}
