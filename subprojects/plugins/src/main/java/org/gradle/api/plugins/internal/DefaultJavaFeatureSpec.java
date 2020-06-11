/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.plugins.internal;

import com.google.common.collect.Lists;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JvmEcosystemUtilities;
import org.gradle.api.tasks.SourceSet;
import org.gradle.internal.component.external.model.ImmutableCapability;

import java.util.List;

public class DefaultJavaFeatureSpec implements FeatureSpecInternal {
    private final String name;
    private final ConfigurationContainer configurationContainer;
    private final List<Capability> capabilities = Lists.newArrayListWithExpectedSize(2);
    private final JvmEcosystemUtilities jvmEcosystemUtilities;

    private boolean overrideDefaultCapability = true;
    private SourceSet sourceSet;
    private boolean withJavadocJar = false;
    private boolean withSourcesJar = false;
    private boolean allowPublication = true;

    public DefaultJavaFeatureSpec(String name,
                                  Capability defaultCapability,
                                  ConfigurationContainer configurationContainer,
                                  JvmEcosystemUtilities jvmEcosystemUtilities) {
        this.name = name;
        this.configurationContainer = configurationContainer;
        this.jvmEcosystemUtilities = jvmEcosystemUtilities;
        this.capabilities.add(defaultCapability);
    }

    @Override
    public void usingSourceSet(SourceSet sourceSet) {
        this.sourceSet = sourceSet;
    }

    @Override
    public void capability(String group, String name, String version) {
        if (overrideDefaultCapability) {
            capabilities.clear();
            overrideDefaultCapability = false;
        }
        capabilities.add(new ImmutableCapability(group, name, version));
    }

    @Override
    public void create() {
        setupConfigurations(sourceSet);
    }

    @Override
    public void withJavadocJar() {
        withJavadocJar = true;
    }

    @Override
    public void withSourcesJar() {
        withSourcesJar = true;
    }

    @Override
    public void disablePublication() {
        allowPublication = false;
    }

    private void setupConfigurations(SourceSet sourceSet) {
        if (sourceSet == null) {
            throw new InvalidUserCodeException("You must specify which source set to use for feature '" + name + "'");
        }

        JvmEcosystemUtilities.JavaComponentSummary javaComponent = jvmEcosystemUtilities.createJavaComponent(name, builder -> {
            builder.usingSourceSet(sourceSet)
                .withDisplayName("feature " + name)
                .exposesApi()
                .withJar();
            if (withJavadocJar) {
                builder.withJavadocJar();
            }
            if (withSourcesJar) {
                builder.withSourcesJar();
            }
            if (allowPublication) {
                builder.published();
            }
            for (Capability capability : capabilities) {
                builder.capability(capability);
            }
        });

        if (SourceSet.isMain(sourceSet)) {
            Configuration impl = javaComponent.getImplementationConfiguration();
            // since we use the main source set, we need to make sure the compile classpath and runtime classpath are properly configured
            configurationContainer.getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME).extendsFrom(impl);
            configurationContainer.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME).extendsFrom(impl);

            // and we also want the feature dependencies to be available on the test classpath
            configurationContainer.getByName(JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME).extendsFrom(impl);
            configurationContainer.getByName(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME).extendsFrom(impl);
        }

    }

}
