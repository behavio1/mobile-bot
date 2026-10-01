package one.behavio.mobilebotui.workspace

import org.junit.Assert.*
import org.junit.Test

class AgentDocumentLinkTest {
    @Test fun resolvesMarkdownInAgentFolder() {
        assertEquals("okf/topic.md", resolveDocumentLink("okf/index.md", "topic.md"))
        assertEquals("results/raport test.MD", resolveDocumentLink("okf/index.md", "../results/raport%20test.MD#wnioski"))
        assertEquals("okf/index.md", resolveDocumentLink("okf/index.md", "#wnioski"))
    }
    @Test fun rejectsEscapesAndOtherSchemes() {
        listOf("../../other/secret.md", "/secret.md", "//other/file.md", "https://site/doc.md", "file:///secret.md", "intent:open", "../secret.json", "%2e%2e/%2e%2e/secret.md", "..%5Csecret.md").forEach {
            assertNull(it, resolveDocumentLink("okf/index.md", it))
        }
    }
}
