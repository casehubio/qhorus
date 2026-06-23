package io.casehub.qhorus.examples;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Multi-model sweep: Ollama 70B — all zones, all variants.
 *
 * <p>
 * Milestone D (#299). Currently disabled — requires Ollama service and model.
 *
 * <p>
 * <strong>To enable:</strong>
 * <ol>
 *   <li>Install Ollama: {@code brew install ollama}</li>
 *   <li>Start the service: {@code ollama serve}</li>
 *   <li>Pull a 70B model: {@code ollama pull llama3.3:70b} (or similar)</li>
 *   <li>In {@code pom.xml}, swap {@code quarkus-langchain4j-jlama} for
 *       {@code quarkus-langchain4j-ollama}</li>
 *   <li>In {@code application.properties}, uncomment the Ollama section</li>
 *   <li>Remove the {@code @Disabled} annotation below</li>
 *   <li>Run: {@code mvn test -Pwith-llm-examples -Dtest=Zone1Zone2Zone3Ollama70BTest}</li>
 * </ol>
 *
 * <p>
 * The test body mirrors {@link Zone1Zone2Zone3Jlama1BTest} exactly — only
 * the model configuration differs. Once the Ollama profile is active, copy
 * the test body and change the MODEL constant to the Ollama model name.
 *
 * <p>
 * Refs #299.
 */
@Disabled("Requires running Ollama service and 70B model — see class Javadoc to enable")
class Zone1Zone2Zone3Ollama70BTest {

    @Test
    void placeholder() {
        // Remove @Disabled on the class and implement following Zone1Zone2Zone3Jlama1BTest.
        // Change MODEL constant to the Ollama model name.
        // Swap pom.xml dependency and uncomment application.properties Ollama section.
    }
}
