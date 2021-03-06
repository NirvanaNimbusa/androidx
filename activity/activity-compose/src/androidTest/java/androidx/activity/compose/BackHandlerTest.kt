/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.activity.compose

import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class BackHandlerTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testBackHandler() {
        var backCounter = 0

        composeTestRule.setContent {
            BackHandler { backCounter++ }
            val dispatcher = LocalOnBackPressedDispatcherOwner.current.onBackPressedDispatcher
            Button(onClick = { dispatcher.onBackPressed() }) {
                Text(text = "Press Back")
            }
        }

        composeTestRule.onNodeWithText("Press Back").performClick()
        composeTestRule.runOnIdle {
            assertThat(backCounter).isEqualTo(1)
        }
    }

    @Test
    fun testBackHandlerDisabledChild() {
        var parentBackCounter = 0
        var childBackCounter = 0

        composeTestRule.setContent {
            BackHandler { parentBackCounter++ }
            val dispatcher = LocalOnBackPressedDispatcherOwner.current.onBackPressedDispatcher
            Button(onClick = { dispatcher.onBackPressed() }) {
                BackHandler(false) { childBackCounter++ }
                Text(text = "Press Back")
            }
        }

        composeTestRule.onNodeWithText("Press Back").performClick()
        composeTestRule.runOnIdle {
            assertThat(parentBackCounter).isEqualTo(1)
            assertThat(childBackCounter).isEqualTo(0)
        }
    }
}
