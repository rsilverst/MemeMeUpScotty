package com.rsilverst.mememeupscotty.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rsilverst.mememeupscotty.ui.theme.MemeMeUpScottyTheme
import com.rsilverst.mememeupscotty.ui.viewmodel.GenerationState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EnergizeButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun idleState_showsGenerateLabelAndIsClickable() {
        var clicks = 0
        composeRule.setContent {
            MemeMeUpScottyTheme {
                EnergizeButton(
                    generationState = GenerationState.Idle,
                    hasGeneratedImage = false,
                    onClick = { clicks++ }
                )
            }
        }

        composeRule.onNodeWithText("ENERGIZE")
            .assertIsEnabled()
            .assertHasClickAction()
            .performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun loadingState_showsCancelLabelAndInvokesOnCancel() {
        var energizeClicks = 0
        var cancelClicks = 0
        composeRule.setContent {
            MemeMeUpScottyTheme {
                EnergizeButton(
                    generationState = GenerationState.Loading,
                    hasGeneratedImage = false,
                    onClick = { energizeClicks++ },
                    onCancel = { cancelClicks++ }
                )
            }
        }

        composeRule.onNodeWithText("CANCEL")
            .assertIsEnabled()
            .assertHasClickAction()
            .performClick()
        assertEquals(1, cancelClicks)
        assertEquals(0, energizeClicks)
    }

    @Test
    fun successState_showsRegenerateLabel() {
        composeRule.setContent {
            MemeMeUpScottyTheme {
                EnergizeButton(
                    generationState = GenerationState.Success(File("ignored")),
                    hasGeneratedImage = true,
                    onClick = {}
                )
            }
        }

        composeRule.onNodeWithText("RE-ENERGIZE").assertIsEnabled()
    }
}
