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
      sh "wget -O ${workspace}/${file_name} ${element.value}"
  }
}
