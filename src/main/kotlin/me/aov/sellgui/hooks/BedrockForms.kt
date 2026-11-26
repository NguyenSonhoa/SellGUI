package me.aov.sellgui.hooks

import org.geysermc.cumulus.form.ModalForm
import org.geysermc.cumulus.response.ModalFormResponse
import org.geysermc.cumulus.response.result.ValidFormResponseResult

internal object BedrockForms {
    fun confirmation(title: String, content: String, sell: String, cancel: String, response: (Boolean) -> Unit): ModalForm =
        ModalForm.builder().title(title).content(content).button1(sell).button2(cancel)
            .resultHandler { _, result -> response(result is ValidFormResponseResult<ModalFormResponse> && result.response().clickedFirst()) }
            .build()
}
