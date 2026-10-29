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
   listOf("OutlinedTextField","OutlinedButton","OutlinedCard","BorderStroke","Modifier.border").forEach{token->assertFalse("${file.name} reintroduced $token",token in text)}
   if(file.name!="AppInputField.kt"){
    assertFalse("${file.name} bypassed AppInputField with BasicTextField",Regex("\\bBasicTextField\\s*\\(").containsMatchIn(text))
    assertFalse("${file.name} bypassed AppInputField with TextField",Regex("(?<!Basic)\\bTextField\\s*\\(").containsMatchIn(text))
   }
   text.lineSequence().filter{"FilterChip(" in it}.forEach{line->assertTrue("${file.name} FilterChip must set border=null","border=null" in line)}
  }
  val component=File(sourceRoot,"AppInputField.kt").readText()
  assertTrue("BasicTextField(" in component)
  assertTrue("if(label!=null)Text(label" in component)
  assertFalse("shared component must not hide Material indicator lines","IndicatorColor" in component||"TextFieldDefaults" in component)
 }
}
