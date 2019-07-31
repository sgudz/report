def reports_map = ["bootstrap_report": env.BOOTSTRAP_REPORT,
                   "kubeconfig": env.KUBECONFIG, "management_logs": env.MANAGEMENT_LOGS]

node () {
  for (element in reports_map) {
      String[] file = element.value.split("/")
      String file_name = file[file.lenght - 1]
      echo "${file_name}"
      echo "${element.key} ${element.value}"
      //wget -O "${workspace}/${filename}" "${element.value}"
  }
}
