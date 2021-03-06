package org.simple.clinic.registration.location

import org.simple.clinic.location.LOCATION_PERMISSION
import org.simple.clinic.platform.util.RuntimePermissionResult
import org.simple.clinic.util.None
import org.simple.clinic.util.Optional
import org.simple.clinic.util.RequiresPermission
import org.simple.clinic.widgets.UiEvent

data class RequestLocationPermission(
    override var permission: Optional<RuntimePermissionResult> = None(),
    override val permissionRequestCode: Int = 1,
    override val permissionString: String = LOCATION_PERMISSION
) : UiEvent, RequiresPermission {
  override val analyticsName = "Registration:Location Access Clicked"
}
