package com.muchq.lunarcat.config;

import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.muchq.json.ObjectMapperModule;
import com.muchq.lunarcat.lifecycle.StartupTask;
import org.jboss.resteasy.plugins.guice.ext.RequestScopeModule;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.ext.Provider;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;

public class LunarCatServiceModule extends AbstractModule {
  private static final Logger LOGGER = LoggerFactory.getLogger(LunarCatServiceModule.class);
  private final Set<String> packagesToScan = Sets.newHashSet("com.muchq.lunarcat");

  public LunarCatServiceModule(String basePackage) {
    this.packagesToScan.add(basePackage);
  }

  @Override
  protected void configure() {
    install(new RequestScopeModule());
    install(new ObjectMapperModule());
    packagesToScan.forEach(this::bindJaxRs);
    bindLifeCycle(Multibinder.newSetBinder(binder(), StartupTask.class));
  }

  private void bindLifeCycle(Multibinder<StartupTask> multibinder) {
    Set<Class<? extends StartupTask>> tasks =
        new Reflections(
            new ConfigurationBuilder()
                .forPackages(packagesToScan.toArray(new String[packagesToScan.size()]))
                .setScanners(new SubTypesScanner(true)))
            .getSubTypesOf(StartupTask.class);

    if (tasks != null) {
      tasks.forEach(multibinder.addBinding()::to);
    }
  }


  private void bindJaxRs(String packageName) {
    Reflections reflections = new Reflections(packageName);
    Set<Class<?>> resources = reflections.getTypesAnnotatedWith(Path.class);
    bindResourcesAndLogEndpoints(resources);
    reflections.getTypesAnnotatedWith(Provider.class).forEach(this::bind);
  }

  private void bindResourcesAndLogEndpoints(Set<Class<?>> resources) {
    LOGGER.info("Binding {} resources...\n", resources.size());

    for (Class<?> clazz : resources) {
      bind(clazz);
      String basePath = clazz.getAnnotation(Path.class).value();
      for (Method method : clazz.getMethods()) {
        Optional<String> methodType = getRequestType(method);
        if (methodType.isPresent()) {
          String wholePath = basePath + getSubPath(method);
          LOGGER.info("          {}    {}", methodType.get(), wholePath);
        }
      }
    }
  }

  private String getSubPath(Method method) {
    if (method.isAnnotationPresent(Path.class)) {
      return method.getAnnotation(Path.class).value();
    }
    return "";
  }

  private Optional<String> getRequestType(Method method) {
    if (method.isAnnotationPresent(GET.class)) {
      return Optional.of("GET");
    } else if (method.isAnnotationPresent(POST.class)) {
      return Optional.of("POST");
    } else if (method.isAnnotationPresent(PUT.class)) {
      return Optional.of("PUT");
    } else if (method.isAnnotationPresent(DELETE.class)) {
      return Optional.of("DELETE");
    } else {
      return Optional.empty();
    }
  }
}
