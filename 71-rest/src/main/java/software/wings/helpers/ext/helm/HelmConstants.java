package software.wings.helpers.ext.helm;

import static org.apache.commons.lang3.StringUtils.EMPTY;

import java.util.concurrent.TimeUnit;

/**
 * Created by anubhaw on 3/23/18.
 */
public final class HelmConstants {
  public static final String HELM_PATH_PLACEHOLDER = "${HELM_PATH}";

  public static final String HELM_NAMESPACE_PLACEHOLDER_REGEX = "\\$\\{NAMESPACE}";
  public static final String HELM_NAMESPACE_PLACEHOLDER = "${NAMESPACE}";
  public static final String HELM_DOCKER_IMAGE_NAME_PLACEHOLDER = "${DOCKER_IMAGE_NAME}";
  public static final String HELM_DOCKER_IMAGE_TAG_PLACEHOLDER = "${DOCKER_IMAGE_TAG}";

  public static final String KUBE_CONFIG_TEMPLATE = "apiVersion: v1\n"
      + "clusters:\n"
      + "- cluster:\n"
      + "    server: ${MASTER_URL}\n"
      + "    insecure-skip-tls-verify: true\n"
      + "  name: CLUSTER_NAME\n"
      + "contexts:\n"
      + "- context:\n"
      + "    cluster: CLUSTER_NAME\n"
      + "    user: HARNESS_USER\n"
      + "    ${NAMESPACE}\n"
      + "  name: CURRENT_CONTEXT\n"
      + "current-context: CURRENT_CONTEXT\n"
      + "kind: Config\n"
      + "preferences: {}\n"
      + "users:\n"
      + "- name: HARNESS_USER\n"
      + "  user:\n"
      + "    ${CLIENT_CERT_DATA}\n"
      + "    ${CLIENT_KEY_DATA}\n"
      + "    ${PASSWORD}\n"
      + "    ${USER_NAME}\n"
      + "    ${SERVICE_ACCOUNT_TOKEN_DATA}";

  public static final class V2Commands {
    // The reason we are using ^ and $ before and after ${RELEASE_NAME} is because helm list doesn't take releaseName as
    // a param and release name becomes a regex
    public static final String HELM_LIST_RELEASE_COMMAND_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} list ${COMMAND_FLAGS} ^${RELEASE_NAME}$";

    public static final String HELM_ROLLBACK_COMMAND_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} rollback ${COMMAND_FLAGS} ${RELEASE} ${REVISION}";
    public static final String HELM_INSTALL_COMMAND_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} install ${COMMAND_FLAGS} ${CHART_REFERENCE} ${OVERRIDE_VALUES} --name ${RELEASE_NAME} ${NAMESPACE}";
    public static final String HELM_UPGRADE_COMMAND_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} upgrade ${COMMAND_FLAGS} ${RELEASE_NAME} ${CHART_REFERENCE} ${OVERRIDE_VALUES}";
    public static final String HELM_RELEASE_HIST_COMMAND_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} hist ${COMMAND_FLAGS} ${RELEASE_NAME} ${FLAGS}";
    public static final String HELM_ADD_REPO_COMMAND_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} repo add ${REPO_NAME} ${REPO_URL}";
    public static final String HELM_REPO_UPDATE_COMMAND_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} repo update";
    public static final String HELM_REPO_LIST_COMMAND_TEMPLATE = "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} repo list";
    public static final String HELM_DELETE_RELEASE_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} delete ${COMMAND_FLAGS} ${FLAGS} ${RELEASE_NAME}";
    public static final String HELM_TEMPLATE_COMMAND_FOR_KUBERNETES_TEMPLATE =
        "${HELM_PATH} template ${CHART_LOCATION}  --name ${RELEASE_NAME} --namespace ${NAMESPACE} ${OVERRIDE_VALUES}";
    public static final String HELM_SEARCH_COMMAND_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} search ${CHART_INFO}";
    public static final String HELM_REPO_ADD_COMMAND_FOR_CHART_MUSEUM =
        "${HELM_PATH} repo add ${REPO_NAME} ${REPO_URL} ${HELM_HOME_PATH_FLAG}";
    public static final String HELM_REPO_ADD_COMMAND_FOR_HTTP =
        "${HELM_PATH} repo add ${REPO_NAME} ${REPO_URL} ${USERNAME} ${PASSWORD} ${HELM_HOME_PATH_FLAG}";
    public static final String HELM_FETCH_COMMAND =
        "${HELM_PATH} fetch ${REPO_NAME}/${CHART_NAME} --untar ${CHART_VERSION} ${HELM_HOME_PATH_FLAG}";
    public static final String HELM_REPO_REMOVE_COMMAND =
        "${HELM_PATH} repo remove ${REPO_NAME} ${HELM_HOME_PATH_FLAG}";
    public static final String HELM_INIT_COMMAND = "${HELM_PATH} init -c --skip-refresh ${HELM_HOME_PATH_FLAG}";
    public static final String HELM_RENDER_SPECIFIC_TEMPLATE =
        "${HELM_PATH} template ${CHART_LOCATION} -x ${CHART_FILE} --name ${RELEASE_NAME} --namespace ${NAMESPACE} ${OVERRIDE_VALUES}";
    public static final String HELM_VERSION_COMMAND_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} version --short ${COMMAND_FLAGS}";
  }

  public static final class V3Commands {
    public static final String HELM_LIST_RELEASE_COMMAND_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} list ${COMMAND_FLAGS} --filter ^${RELEASE_NAME}$";
    public static final String HELM_ROLLBACK_COMMAND_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} rollback  ${RELEASE} ${REVISION} ${COMMAND_FLAGS}";
    public static final String HELM_INSTALL_COMMAND_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} install  ${RELEASE_NAME} ${CHART_REFERENCE}  ${COMMAND_FLAGS} ${OVERRIDE_VALUES} ${NAMESPACE}";
    public static final String HELM_UPGRADE_COMMAND_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} upgrade ${COMMAND_FLAGS} ${RELEASE_NAME} ${CHART_REFERENCE} ${OVERRIDE_VALUES}";
    public static final String HELM_RELEASE_HIST_COMMAND_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} hist ${RELEASE_NAME} ${COMMAND_FLAGS}  ${FLAGS}";
    public static final String HELM_ADD_REPO_COMMAND_TEMPLATE = "${HELM_PATH} repo add ${REPO_NAME} ${REPO_URL}";
    public static final String HELM_REPO_UPDATE_COMMAND_TEMPLATE = "${HELM_PATH} repo update";
    public static final String HELM_REPO_LIST_COMMAND_TEMPLATE = "${HELM_PATH} repo list";
    public static final String HELM_DELETE_RELEASE_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} uninstall ${RELEASE_NAME} ${COMMAND_FLAGS}";
    public static final String HELM_TEMPLATE_COMMAND_FOR_KUBERNETES_TEMPLATE =
        "${HELM_PATH} template ${RELEASE_NAME} ${CHART_LOCATION}  --namespace ${NAMESPACE} ${OVERRIDE_VALUES}";
    public static final String HELM_SEARCH_COMMAND_TEMPLATE = "${HELM_PATH} search repo ${CHART_INFO}";
    public static final String HELM_REPO_ADD_COMMAND_FOR_CHART_MUSEUM =
        "${HELM_PATH} repo add ${REPO_NAME} ${REPO_URL}";
    public static final String HELM_REPO_ADD_COMMAND_FOR_HTTP =
        "${HELM_PATH} repo add ${REPO_NAME} ${REPO_URL} ${USERNAME} ${PASSWORD}";
    public static final String HELM_FETCH_COMMAND =
        "${HELM_PATH} pull ${REPO_NAME}/${CHART_NAME} --untar ${CHART_VERSION}";
    public static final String HELM_REPO_REMOVE_COMMAND = "${HELM_PATH} repo remove ${REPO_NAME}";
    public static final String HELM_INIT_COMMAND = EMPTY;
    public static final String HELM_RENDER_SPECIFIC_TEMPLATE =
        "${HELM_PATH} template ${RELEASE_NAME} ${CHART_LOCATION} -s ${CHART_FILE} --namespace ${NAMESPACE} ${OVERRIDE_VALUES}";
    public static final String HELM_VERSION_COMMAND_TEMPLATE = "${HELM_PATH} version --short ${COMMAND_FLAGS}";

    private V3Commands() {
      throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
  }

  public static final long DEFAULT_HELM_COMMAND_TIMEOUT = TimeUnit.MINUTES.toMillis(30);
  public static final long DEFAULT_TILLER_CONNECTION_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(60);
  public static final String HELM_RELEASE_LABEL = "release";

  public static final String DEFAULT_HELM_VALUE_YAML = "# Enter your Helm value YAML\n"
      + "#\n"
      + "# Placeholders:\n"
      + "#\n"
      + "# Optional: ${NAMESPACE}\n"
      + "#   - Replaced with the namespace\n"
      + "#     Harness will set the namespace from infrastructure\n"
      + "#     mapping namespace\n"
      + "#\n"
      + "# Optional: ${DOCKER_IMAGE_NAME}\n"
      + "#   - Replaced with the Docker image name\n"
      + "#\n"
      + "# Optional: ${DOCKER_IMAGE_TAG}\n"
      + "#   - Replaced with the Docker image tag\n"
      + "#\n"
      + "# ---\n"
      + "namespace : ${NAMESPACE}\n";
}
