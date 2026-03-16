package com.phonepe.drove.executor.resourcemgmt.resourceloaders;

import lombok.val;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommandUtilsTest {

	@Test
	void testParseCommandOutput() throws Exception {
		val out = CommandUtils.parseCommandOutput("echo hello", true);
		assertEquals(1, out.size());
		assertEquals("hello", out.get(0));
	}

	@Test
	void testParseCommandOutputHonoursConfiguredTimeout() {
		assertThrows(IOException.class,
				() -> CommandUtils.parseCommandOutput("sleep 5", true, 1));
	}

}