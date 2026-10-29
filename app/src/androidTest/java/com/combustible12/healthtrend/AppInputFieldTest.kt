package com.combustible12.healthtrend

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class AppInputFieldTest {
 @get:Rule val compose=createComposeRule()

 @Test fun normalFocusedErrorAndDisabledStatesKeepExternalLabelsAndInputBehavior(){
  compose.setContent{MaterialTheme{Column{
   var value by remember{mutableStateOf("")}
   AppInputField(value,{value=it},Modifier.testTag("normal"),label="普通输入",singleLine=true)
   AppInputField("错误值",{},Modifier.testTag("error"),label="错误输入",isError=true,singleLine=true)
   AppInputField("不可编辑",{},Modifier.testTag("disabled"),label="禁用输入",enabled=false,singleLine=true)
  }}}
  listOf("普通输入","错误输入","禁用输入").forEach{compose.onNodeWithText(it).assertIsDisplayed()}
  val normal=compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("normal")))
  normal.performClick().assertIsFocused().performTextInput("123").assertTextContains("123")
  compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("disabled"))).assertIsNotEnabled()
 }
}
