package com.giyeok.passzero2.gui

import java.awt.Font
import java.io.File
import java.util.concurrent.ExecutorService

class Config(
  val localInfoFile: File = File("./localInfo.p0"),
  val executors: ExecutorService
) {
  val defaultFont = Font(Font.MONOSPACED, 0, 15)
  val bigFont = Font(Font.MONOSPACED, 0, 24)

  fun getString(stringKey: String): String = when (stringKey) {
    "app_title" -> "Passzero"
    "ENTRY_NAME" -> "Name"
    "ENTRY_TYPE" -> "Type"
    "ENTRY_TYPE_LOGIN" -> "Login"
    "ENTRY_TYPE_NOTE" -> "Note"
    "ENTRY_TYPE_UNSPECIFIED" -> "Unspecified"
    "ENTRY_DETAIL_ITEM_USERNAME" -> "Username"
    "ENTRY_DETAIL_ITEM_PASSWORD" -> "Password"
    "ENTRY_DETAIL_ITEM_WEBSITE" -> "Website"
    "ENTRY_DETAIL_ITEM_NOTE" -> "Note"
    "ENTRY_DETAIL_ITEM_UNKNOWN" -> "Unspecified"
    "ENTRY_EDIT" -> "Edit this entry"
    "ENTRY_DELETE" -> "Delete this entry"
    "ENTRY_CREATE_SAVE" -> "Create"
    "ENTRY_CREATE_CANCEL" -> "Cancel"
    "ENTRY_EDIT_SAVE" -> "Save"
    "ENTRY_EDIT_CANCEL" -> "Cancel"
    "ENTRY_DETAIL_NEW_ITEM" -> "Add"
    "CONFIRM_DELETE_ENTRY" -> "Do you really want to delete entry \"%s\"?"
    "SELECT_ENTRY" -> "Select an entry"
    "NEW_ENTRY" -> "New"
    "LOCAL_SECRET" -> "Local Secret"
    "REGENERATE_LOCAL_SECRET" -> "Regenerate Local Secret"
    "DROPBOX_APP_KEY" -> "Dropbox App Key"
    "DROPBOX_REDIRECT_URI" -> "Dropbox Redirect URI"
    "DROPBOX_AUTH_URI" -> "Dropbox Authorize URI"
    "DROPBOX_AUTHORIZATION_CODE" -> "Dropbox Authorization Code"
    "DROPBOX_ACCESS_TOKEN_STATUS" -> "Dropbox Auth Status"
    "DROPBOX_TOKEN_NOT_READY" -> "Not OK"
    "DROPBOX_TOKEN_OK" -> "OK"
    "DROPBOX_APP_ROOT_PATH" -> "Dropbox App Root Path"
    "MASTER_PASSWORD" -> "Master Password"
    "MASTER_PASSWORD_VERIFY" -> "Verify Master Password"
    "GENERATE_LOCAL_INFO" -> "Generate Local Info"
    else -> stringKey
  }
}