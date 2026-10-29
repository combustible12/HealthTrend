package com.combustible12.healthtrend

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiDesignRuleTest {
 @Test fun `all app inputs use the borderless shared component`() {
  val sourceRoot=listOf(File("app/src/main/java/com/combustible12/healthtrend"),File("src/main/java/com/combustible12/healthtrend")).first{it.isDirectory}
  val sources=sourceRoot.listFiles().orEmpty().filter{it.extension=="kt"}
  assertTrue(sources.isNotEmpty())
  sources.forEach{file->
   val text=file.readText()
   assertFalse("${file.name} reintroduced a forbidden outlined field","OutlinedTextField" in text)
   if(file.name!="AppInputField.kt")assertFalse("${file.name} bypassed AppInputField",Regex("(?<![A-Za-z])TextField\\s*\\(").containsMatchIn(text))
  }
  val component=File(sourceRoot,"AppInputField.kt").readText()
  assertTrue("focusedIndicatorColor=Color.Transparent" in component)
  assertTrue("if(label!=null)Text(label" in component)
 }
}
