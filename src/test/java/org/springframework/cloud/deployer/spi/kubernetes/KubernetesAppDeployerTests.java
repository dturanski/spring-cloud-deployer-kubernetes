package org.springframework.cloud.deployer.spi.kubernetes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import org.junit.Test;
import org.springframework.boot.bind.YamlConfigurationFactory;
import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for {@link KubernetesAppDeployer}
 *
 * @author Donovan Muller
 */
public class KubernetesAppDeployerTests {

	private KubernetesAppDeployer deployer;

	@Test
	public void deployWithVolumesOnly() throws Exception {
		AppDefinition definition = new AppDefinition("app-test", null);
		AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(),
				new HashMap<>());

		deployer = new KubernetesAppDeployer(bindDeployerProperties(), null);
		PodSpec podSpec = deployer.createPodSpec("1", appDeploymentRequest, 8080, 1, false);

		assertThat(podSpec.getVolumes()).isEmpty();
	}

	@Test
	public void deployWithVolumesAndVolumeMounts() throws Exception {
		AppDefinition definition = new AppDefinition("app-test", null);
		Map<String, String> props = new HashMap<>();
		props.put("spring.cloud.deployer.kubernetes.volumeMounts",
				"["
					+ "{name: 'testpvc', mountPath: '/test/pvc'}, "
					+ "{name: 'testnfs', mountPath: '/test/nfs', readOnly: 'true'}"
				+ "]");
		AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

		deployer = new KubernetesAppDeployer(bindDeployerProperties(), null);
		PodSpec podSpec = deployer.createPodSpec("1", appDeploymentRequest, 8080, 1, false);

		assertThat(podSpec.getVolumes()).containsOnly(
				// volume 'testhostpath' defined in dataflow-server.yml should not be added
				// as there is no corresponding volume mount
				new VolumeBuilder().withName("testpvc").withNewPersistentVolumeClaim("testClaim", true).build(),
				new VolumeBuilder().withName("testnfs").withNewNfs("/test/nfs", null, "10.0.0.1:111").build());

		props.clear();
		props.put("spring.cloud.deployer.kubernetes.volumes",
				"["
					+ "{name: testhostpath, hostPath: { path: '/test/override/hostPath' }},"
					+ "{name: 'testnfs', nfs: { server: '192.168.1.1:111', path: '/test/override/nfs' }} "
				+ "]");
		props.put("spring.cloud.deployer.kubernetes.volumeMounts",
				"["
					+ "{name: 'testhostpath', mountPath: '/test/hostPath'}, "
					+ "{name: 'testpvc', mountPath: '/test/pvc'}, "
					+ "{name: 'testnfs', mountPath: '/test/nfs', readOnly: 'true'}"
				+ "]");
		appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

		deployer = new KubernetesAppDeployer(bindDeployerProperties(), null);
		podSpec = deployer.createPodSpec("1", appDeploymentRequest, 8080, 1, false);

		assertThat(podSpec.getVolumes()).containsOnly(
				new VolumeBuilder().withName("testhostpath").withNewHostPath("/test/override/hostPath").build(),
				new VolumeBuilder().withName("testpvc").withNewPersistentVolumeClaim("testClaim", true).build(),
				new VolumeBuilder().withName("testnfs").withNewNfs("/test/override/nfs", null, "192.168.1.1:111").build());
	}

	@Test
	public void deployWithNodeSelector() throws Exception {
		AppDefinition definition = new AppDefinition("app-test", null);
		Map<String, String> props = new HashMap<>();
		props.put("spring.cloud.deployer.kubernetes.deployment.nodeSelector",
				"disktype:ssd, os: linux");
		AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);

		deployer = new KubernetesAppDeployer(bindDeployerProperties(), null);
		PodSpec podSpec = deployer.createPodSpec("1", appDeploymentRequest, 8080, 1, false);

		assertThat(podSpec.getNodeSelector()).containsOnly(
				entry("disktype", "ssd"),
				entry("os", "linux")
		);

	}

	@Test
	public void deployWithSidecars() throws Exception {
		AppDefinition definition = new AppDefinition("app-test", null);
		Map<String, String> props = new HashMap<>();
		props.put("spring.cloud.deployer.kubernetes.side-cars",
				"{"
					+ "sidecar0: {image: 'docker://sidecars/sidecar0:latest'},"
					+ "sidecar1: {image: 'docker://sidecars/sidecar1:latest'}"
				+ "}");
		AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, getResource(), props);
		deployer = new KubernetesAppDeployer(bindDeployerProperties(), null);
		PodSpec podSpec = deployer.createPodSpec("1", appDeploymentRequest, 8080, 1, false);
		assertThat(podSpec.getContainers().size()).isEqualTo(3);
	}

	private Resource getResource() {
		return new DockerResource("springcloud/spring-cloud-deployer-spi-test-app:latest");
	}

	private KubernetesDeployerProperties bindDeployerProperties() throws Exception {
		YamlConfigurationFactory<KubernetesDeployerProperties> yamlConfigurationFactory = new YamlConfigurationFactory<>(
				KubernetesDeployerProperties.class);
		yamlConfigurationFactory.setResource(new ClassPathResource("dataflow-server.yml"));
		yamlConfigurationFactory.afterPropertiesSet();
		return yamlConfigurationFactory.getObject();
	}
}
