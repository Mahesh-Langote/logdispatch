package in.maheshlangote.logdispatch.config;

import in.maheshlangote.logdispatch.LogDispatchAspect;
import in.maheshlangote.logdispatch.LogDispatchHealthController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class LogDispatchAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LogDispatchAutoConfiguration.class));

    @Test
    void shouldEnableLogDispatchByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(LogDispatchProperties.class);
            assertThat(context.getBean(LogDispatchProperties.class).isEnabled()).isTrue();
        });
    }

    @Test
    void shouldRegisterBeansWithDisabledProperties() {
        contextRunner
                .withPropertyValues("logdispatch.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(LogDispatchAspect.class);
                    assertThat(context).hasSingleBean(LogDispatchHealthController.class);
                    assertThat(context).hasBean("logDispatchFilterRegistration");
                    assertThat(context.getBean(LogDispatchProperties.class).isEnabled()).isFalse();
                });
    }
}
