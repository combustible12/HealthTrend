package com.combustible12.healthtrend

/** All update entry points reject reinstall/downgrade; a cache cannot hide a newer release. */
internal object UpdateVersionPolicy {
 fun isNewer(candidate:Long,current:Long)=candidate>current
 fun canReuse(candidate:Long,current:Long,latest:Long?)=
  isNewer(candidate,current)&&(latest==null||candidate==latest)
}
