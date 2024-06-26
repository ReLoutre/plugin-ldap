package io.kestra.plugin.ldapManager;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.net.URI;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import static org.junit.jupiter.api.Assertions.fail;

@KestraTest
public class IonToLdifTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private StorageInterface storageInterface;

    /**
     * Insert provided contents in separated files in the Kestra storage.
     * @param contents : A list of string to input in Kestra files.
     * @return A new context where each newly created file may be accessed with a pebble expression like {{ file0 }}, {{ file1 }}, {{ fileEtc }}
     */
    private RunContext getRunContext(List<String> contents) {
        Map<String, String> kestraPaths = new HashMap<>();
        Integer idx = 0;
        for (String content : contents) {
            URI filePath;
            try {
                filePath = this.storageInterface.put(
                    null, 
                    URI.create("/" + IdUtils.create() + ".ldif"), 
                    new ByteArrayInputStream(content.getBytes())
                );
                kestraPaths.put("file" + idx, filePath.toString());
                idx++;
            } catch (IOException e) {
                System.err.println(e.getMessage());
                fail("Unable to load refs files.");
                return null;
            }
        }
        return this.runContextFactory.of(ImmutableMap.copyOf(kestraPaths));
    }

    /**
     * Assert the equality between result file(s) content provided by a IonToLdif transformation task and string(s).
     * @param expected_results : Strings representing the expected content of each transformation.
     * @param runOutput : The output of the transformation task to make the comparison with.
     */
    private void assertFilesEq(IonToLdif.Output runOutput, List<String> expected_results) {
        List<URI> results = runOutput.getUrisList();
        Integer idx = 0;
        for (String expected_result : expected_results) {
            assertThat("Result file should exist", this.storageInterface.exists(null, results.get(idx)), is(true));
            try (InputStream streamResult = this.storageInterface.get(null, results.get(idx))) {
                String result = new String(streamResult.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");

                System.out.println("Got :\n" + result);
                System.out.println("Expecting :\n" + expected_result);
                idx++;
                assertThat("Result should match the reference", result.equals(expected_result));
            } catch (IOException e) {
                System.err.println(e.getMessage());
                fail("Unable to load results files.");
            }
        }
    }

    @Test
    void basic_test() throws Exception {
        List<String> inputs = new ArrayList<>();
        List<String> expectations = new ArrayList<>();
        List<String> kestraFilepaths = new ArrayList<>();

        // specific test values :
        inputs.add("""
            {dn:"cn=bob@orga.com,ou=diffusion_list,dc=orga,dc=com",attributes:{description:["Some description 1","Melusine lover"],someOtherAttribute:["perhaps","perhapsAgain"]}}
            {dn:"cn=tony@orga.com,ou=diffusion_list,dc=orga,dc=com",attributes:{description:["Some description 2","Melusine lover as well"],someOtherAttribute:["perhaps 2","perhapsAgain 2"]}}""");// fst file
        inputs.add("""
            {dn:"cn=triss@orga.com,ou=diffusion_list,dc=orga,dc=com",attributes:{description:["Some description 3"],someOtherAttribute:["Melusine lover, obviously"]}}
            {dn:"cn=yennefer@orga.com,ou=diffusion_list,dc=orga,dc=com",attributes:{description:["Some description 2"],someOtherAttribute:["Loves herself"]}}""");// scnd file
        expectations.add("""
            dn: cn=bob@orga.com,ou=diffusion_list,dc=orga,dc=com
            description: Some description 1
            description: Melusine lover
            someOtherAttribute: perhaps
            someOtherAttribute: perhapsAgain

            dn: cn=tony@orga.com,ou=diffusion_list,dc=orga,dc=com
            description: Some description 2
            description: Melusine lover as well
            someOtherAttribute: perhaps 2
            someOtherAttribute: perhapsAgain 2

            """);// fst file
        expectations.add("""
            dn: cn=triss@orga.com,ou=diffusion_list,dc=orga,dc=com
            description: Some description 3
            someOtherAttribute: Melusine lover, obviously

            dn: cn=yennefer@orga.com,ou=diffusion_list,dc=orga,dc=com
            description: Some description 2
            someOtherAttribute: Loves herself

            """);// scnd file
        /////////////////////////

        RunContext runContext = getRunContext(inputs);
        for (Integer i = 0; i < inputs.size(); i++) {
            kestraFilepaths.add(String.format("{{file%d}}", i));
        }
        IonToLdif task = IonToLdif.builder().inputs(kestraFilepaths).build();
        IonToLdif.Output runOutput = task.run(runContext);
        assertFilesEq(runOutput, expectations);
    }
}
