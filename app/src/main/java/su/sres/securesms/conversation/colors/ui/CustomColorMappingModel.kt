package su.sres.securesms.conversation.colors.ui

import su.sres.securesms.util.MappingModel

class CustomColorMappingModel : MappingModel<CustomColorMappingModel> {
  override fun areItemsTheSame(newItem: CustomColorMappingModel): Boolean {
    return true
  }

  override fun areContentsTheSame(newItem: CustomColorMappingModel): Boolean {
    return true
  }
}