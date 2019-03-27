package org.springframework.cloud.deployer.spi.kubernetes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author David Turanski
 **/
@SpringBootTest(classes = { KubernetesAutoConfiguration.class }, properties = {
		"spring.cloud.deployer.kubernetes.maximum-concurrent-tasks=10" })
@RunWith(SpringRunner.class)
public class KubernetesTaskLauncherMaximumConcurrentTasksTests {

	@Autowired
	private TaskLauncher taskLauncher;

	@MockBean
	private KubernetesClient client;

	private List<Pod> pods;

	@Rule
	public ExpectedException expectedException = ExpectedException.none();

	@Test
	public void getMaximumConcurrentTasksExceeded() {
		assertThat(taskLauncher).isNotNull();

		pods = stubForRunningPods(10);

		MixedOperation podsOperation = mock(MixedOperation.class);
		FilterWatchListDeletable filterWatchListDeletable = mock(FilterWatchListDeletable.class);
		when(podsOperation.withLabel("task-name")).thenReturn(filterWatchListDeletable);
		when(filterWatchListDeletable.list()).thenAnswer(invocation -> {
			PodList podList = new PodList();
			List<Pod> items = new ArrayList<>();
			podList.setItems(pods);
			return podList;
		});

		when(client.pods()).thenReturn(podsOperation);

		when(podsOperation.withName(anyString())).thenAnswer(invocation -> {
			Pod p = pods.stream().filter(pod -> pod.getMetadata().getName().equals(invocation.getArgument(0)))
					.findFirst().orElse(null);
			PodResource podResource = mock(PodResource.class);
			when(podResource.get()).thenReturn(p);
			return podResource;
		});

		int executionCount = taskLauncher.getRunningTaskExecutionCount();

		assertThat(executionCount).isEqualTo(10);

		assertThat(taskLauncher.getMaximumConcurrentTasks()).isEqualTo(taskLauncher.getRunningTaskExecutionCount());

		expectedException.expect(IllegalStateException.class);
		expectedException.expectMessage(
				"Cannot launch task task. The maximum concurrent task executions is at its limit [10].");

		AppDefinition appDefinition = new AppDefinition("task", Collections.emptyMap());
		AppDeploymentRequest request = new AppDeploymentRequest(appDefinition, mock(Resource.class),
				Collections.emptyMap());

		taskLauncher.launch(request);
	}

	private List<Pod> stubForRunningPods(int numTasks) {
		List<Pod> items = new ArrayList<>();
		for (int i = 0; i < numTasks; i++) {
			items.add(new PodBuilder().withNewMetadata()
					.withName("task-" + i).endMetadata()
					.withNewStatus()
					.withPhase("Running")
					.endStatus().build());
		}
		return items;
	}
}
