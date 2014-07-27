package zoey

import org.scalatest.FunSpec

class ZNodeOrderingSpec extends FunSpec {
  describe("ZNode ordering") {
    it ("should support sequential ordering by default") {
      val a = ZNode(null, s"/x-000")
      val b = ZNode(null, s"/x-11")
      val c = ZNode(null, s"/x-102")
      assert(Seq(c, b, a).sorted === Seq(a, b, c))
    }
  }
}
