/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.compose.home
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.foundation.layout.offset
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Text
//import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
//import androidx.compose.ui.text.style.TextOverflow
//import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mozilla.fenix.R
import org.mozilla.fenix.theme.FirefoxTheme


@Composable
fun HomeSectionHeader(
    description: String = "",
    onShowAllClick: (() -> Unit)? = null,
    onShowAllLongPress: (() -> Unit)? = null,
    onShowAllDoubleClick: (() -> Unit)? = null,

    ) {
    HomeSectionHeaderContent(
       // headerText = headerText,
        description = description,
        onShowAllClick = onShowAllClick,
        onShowAllLongPress = onShowAllLongPress,
        onShowAllDoubleClick = onShowAllDoubleClick

    )
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeSectionHeaderContent(
    //textColor: Color = FirefoxTheme.colors.textWarning,
    description: String = "",
    showAllTextColor: Color = FirefoxTheme.colors.textAccent,
    onShowAllClick: (() -> Unit)? = null,
    onShowAllLongPress: (() -> Unit)? = null,
    onShowAllDoubleClick: (() -> Unit)?,

    ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {


        onShowAllClick?.let {
            Text(
                text = stringResource(id = R.string.recent_tabs_show_all),
                modifier = Modifier
                    //.padding(start = 900.dp)
                    //.offset(y = (-12).dp)
                    .semantics {
                        contentDescription = description
                    }
                    .combinedClickable(
                        onClick = { onShowAllClick.invoke() },
                        onLongClick = { onShowAllLongPress?.invoke() },
                        onDoubleClick = { onShowAllDoubleClick?.invoke() }
                    ),
                style = TextStyle(
                    color = showAllTextColor,
                    fontSize = 15.sp,
                )
            )
        }

    }
}
