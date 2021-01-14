/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import com.intellij.refactoring.BaseRefactoringProcessor
import org.intellij.lang.annotations.Language
import org.rust.MockAdditionalCfgOptions
import org.rust.MockEdition
import org.rust.RsTestBase
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.ide.refactoring.changeSignature.Parameter
import org.rust.ide.refactoring.changeSignature.RsChangeFunctionSignatureConfig
import org.rust.ide.refactoring.changeSignature.withMockChangeFunctionSignature
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement

class RsChangeSignatureTest : RsTestBase() {
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test unavailable if a parameter is cfg-disabled`() = checkError("""
        fn foo/*caret*/(#[cfg(not(intellij_rust))] a: u32) {}
    """, """Cannot perform refactoring.
Cannot change signature of function with cfg-disabled parameters""")

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test available if a parameter is cfg-enabled`() = doTest("""
        fn foo/*caret*/(#[cfg(intellij_rust)] a: u32) {}
    """, """
        fn bar(#[cfg(intellij_rust)] a: u32) {}
    """) {
        name = "bar"
    }

    fun `test do not change anything`() = doTest("""
        async unsafe fn foo/*caret*/(a: u32, b: bool) -> u32 { 0 }
        fn bar() {
            unsafe { foo(1, true); }
        }
    """, """
        async unsafe fn foo(a: u32, b: bool) -> u32 { 0 }
        fn bar() {
            unsafe { foo(1, true); }
        }
    """) {}

    fun `test rename function reference`() = doTest("""
        fn foo/*caret*/() {}
        fn id<T>(t: T) {}

        fn baz() {
            id(foo)
        }
    """, """
        fn bar/*caret*/() {}
        fn id<T>(t: T) {}

        fn baz() {
            id(bar)
        }
    """) {
        name = "bar"
    }

    fun `test rename function import`() = doTest("""
        mod bar {
            pub fn foo/*caret*/() {}
        }
        use bar::{foo};
    """, """
        mod bar {
            pub fn baz/*caret*/() {}
        }
        use bar::{baz};
    """) {
        name = "baz"
    }

    fun `test rename function`() = doTest("""
        fn foo/*caret*/() {}
    """, """
        fn bar() {}
    """) {
        name = "bar"
    }

    fun `test rename function change usage`() = doTest("""
        fn foo/*caret*/() {}
        fn test() {
            foo()
        }
    """, """
        fn bar() {}
        fn test() {
            bar()
        }
    """) {
        name = "bar"
    }

    fun `test rename function change complex path usage`() = doTest("""
        mod inner {
            pub fn foo/*caret*/() {}
        }
        fn test() {
            inner::foo()
        }
    """, """
        mod inner {
            pub fn bar() {}
        }
        fn test() {
            inner::bar()
        }
    """) {
        name = "bar"
    }

    fun `test rename method change usage`() = doTest("""
        struct S;
        impl S {
            fn foo/*caret*/(&self) {}
        }

        fn test(s: S) {
            s.foo();
        }
    """, """
        struct S;
        impl S {
            fn bar(&self) {}
        }

        fn test(s: S) {
            s.bar();
        }
    """) {
        name = "bar"
    }

    fun `test change visibility`() = doTest("""
        pub fn foo/*caret*/() {}
    """, """
        pub(crate) fn foo() {}
    """) {
        visibility = createVisibility("pub(crate)")
    }

    fun `test remove visibility`() = doTest("""
        pub fn foo/*caret*/() {}
    """, """
        fn foo() {}
    """) {
        visibility = null
    }

    fun `test change return type`() = doTest("""
        fn foo/*caret*/() -> i32 { 0 }
    """, """
        fn foo() -> u32 { 0 }
    """) {
        returnTypeDisplay = createType("u32")
    }

    fun `test change return type lifetime`() = doTest("""
        fn foo<'a, 'b>/*caret*/(a: &'a u32, b: &'b u32) -> &'a i32 { 0 }
    """, """
        fn foo<'a, 'b>(a: &'a u32, b: &'b u32) -> &'b i32 { 0 }
    """) {
        returnTypeDisplay = createType("&'b i32")
    }

    fun `test add return type`() = doTest("""
        fn foo/*caret*/() {}
    """, """
        fn foo() -> u32 {}
    """) {
        returnTypeDisplay = createType("u32")
    }

    fun `test add return type with lifetime`() = doTest("""
        fn foo/*caret*/<'a>(a: &'a u32) { a }
                          //^
    """, """
        fn foo/*caret*/<'a>(a: &'a u32) -> &'a u32 { a }
                          //^
    """) {
        returnTypeDisplay = createType("&'a u32")
    }

    fun `test add return type with default type arguments`() = doTest("""
        struct S<T, R=u32>(T, R);
        fn foo/*caret*/(s: S<bool>) { unimplemented!() }
                      //^
    """, """
        struct S<T, R=u32>(T, R);
        fn foo/*caret*/(s: S<bool>) -> S<bool> { unimplemented!() }
                      //^
    """) {
        val parameter = findElementInEditor<RsValueParameter>()
        returnTypeDisplay = parameter.typeReference!!
    }

    fun `test remove return type`() = doTest("""
        fn foo/*caret*/() -> u32 { 0 }
    """, """
        fn foo() { 0 }
    """) {
        returnTypeDisplay = createType("()")
    }

    fun `test remove return type without block`() = doTest("""
        trait Trait {
            fn foo/*caret*/() -> i32;
        }
    """, """
        trait Trait {
            fn foo() -> u32;
        }
    """) {
        returnTypeDisplay = createType("u32")
    }

    fun `test remove only parameter`() = doTest("""
        fn foo/*caret*/(a: u32) {
            let c = a;
        }
        fn bar() {
            foo(0);
        }
    """, """
        fn foo() {
            let c = a;
        }
        fn bar() {
            foo();
        }
    """) {
        parameters.removeAt(0)
    }

    fun `test remove first parameter`() = doTest("""
        fn foo/*caret*/(a: u32, b: u32) {
            let c = a;
        }
        fn bar() {
            foo(0, 1);
        }
    """, """
        fn foo(b: u32) {
            let c = a;
        }
        fn bar() {
            foo(1);
        }
    """) {
        parameters.removeAt(0)
    }

    fun `test remove middle parameter`() = doTest("""
        fn foo/*caret*/(a: u32, b: u32, c: u32) {
            let c = a;
        }
        fn bar() {
            foo(0, 1, 2);
        }
    """, """
        fn foo(a: u32, c: u32) {
            let c = a;
        }
        fn bar() {
            foo(0, 2);
        }
    """) {
        parameters.removeAt(1)
    }

    fun `test remove last parameter`() = doTest("""
        fn foo/*caret*/(a: u32, b: u32) {}
        fn bar() {
            foo(0, 1);
        }
    """, """
        fn foo(a: u32) {}
        fn bar() {
            foo(0);
        }
    """) {
        parameters.removeAt(parameters.size - 1)
    }

    fun `test remove last parameter trailing comma`() = doTest("""
        fn foo/*caret*/(a: u32,) {}
    """, """
        fn foo() {}
    """) {
        parameters.clear()
    }

    fun `test add only parameter`() = doTest("""
        fn foo/*caret*/() {}
        fn bar() {
            foo();
        }
    """, """
        fn foo(a: u32) {}
        fn bar() {
            foo();
        }
    """) {
        parameters.add(parameter("a", createType("u32")))
    }

    fun `test add last parameter`() = doTest("""
        fn foo/*caret*/(a: u32) {}
        fn bar() {
            foo(0);
        }
    """, """
        fn foo(a: u32, b: u32) {}
        fn bar() {
            foo(0, );
        }
    """) {
        parameters.add(parameter("b", createType("u32")))
    }

    fun `test add multiple parameters`() = doTest("""
        fn foo/*caret*/(a: u32) {}
        fn bar() {
            foo(0);
        }
    """, """
        fn foo(a: u32, b: u32, c: u32) {}
        fn bar() {
            foo(0, , );
        }
    """) {
        parameters.add(parameter("b", createType("u32")))
        parameters.add(parameter("c", createType("u32")))
    }

    fun `test add parameter with lifetime`() = doTest("""
        fn foo/*caret*/<'a>(a: &'a u32) {}
                          //^
    """, """
        fn foo/*caret*/<'a>(a: &'a u32, b: &'a u32) {}
                          //^
    """) {
        val parameter = findElementInEditor<RsValueParameter>()
        parameters.add(parameter("b", parameter.typeReference!!))
    }

    fun `test add parameter with default type arguments`() = doTest("""
        struct S<T, R=u32>(T, R);
        fn foo/*caret*/(a: S<bool>) { unimplemented!() }
                      //^
    """, """
        struct S<T, R=u32>(T, R);
        fn foo/*caret*/(a: S<bool>, b: S<bool>) { unimplemented!() }
                      //^
    """) {
        val parameter = findElementInEditor<RsValueParameter>()
        parameters.add(parameter("b", parameter.typeReference!!))
    }

    fun `test add parameter to method`() = doTest("""
        struct S;
        impl S {
            fn foo/*caret*/(&self) {}
        }
        fn bar(s: S) {
            s.foo();
        }
    """, """
        struct S;
        impl S {
            fn foo/*caret*/(&self, a: u32) {}
        }
        fn bar(s: S) {
            s.foo();
        }
    """) {
        parameters.add(parameter("a", createType("u32")))
    }

    fun `test swap parameters`() = doTest("""
        fn foo/*caret*/(a: u32, b: u32) {}
        fn bar() {
            foo(0, 1);
        }
    """, """
        fn foo(b: u32, a: u32) {}
        fn bar() {
            foo(1, 0);
        }
    """) {
        swapParameters(0, 1)
    }

    fun `test swap method parameters`() = doTest("""
        struct S;
        impl S {
            fn foo/*caret*/(&self, a: u32, b: u32) {}
        }
        fn bar(s: S) {
            s.foo(0, 1);
        }
    """, """
        struct S;
        impl S {
            fn foo/*caret*/(&self, b: u32, a: u32) {}
        }
        fn bar(s: S) {
            s.foo(1, 0);
        }
    """) {
        swapParameters(0, 1)
    }

    fun `test swap method parameters UFCS`() = doTest("""
        struct S;
        impl S {
            fn foo/*caret*/(&self, a: u32, b: u32) {}
        }
        fn bar(s: S) {
            S::foo(&s, 0, 1);
        }
    """, """
        struct S;
        impl S {
            fn foo/*caret*/(&self, b: u32, a: u32) {}
        }
        fn bar(s: S) {
            S::foo(&s, 1, 0);
        }
    """) {
        swapParameters(0, 1)
    }

    fun `test add method parameter UFCS`() = doTest("""
        struct S;
        impl S {
            fn foo/*caret*/(&self) {}
        }
        fn bar(s: S) {
            S::foo(&s);
        }
    """, """
        struct S;
        impl S {
            fn foo/*caret*/(&self, a: u32) {}
        }
        fn bar(s: S) {
            S::foo(&s, );
        }
    """) {
        parameters.add(parameter("a", createType("u32")))
    }

    fun `test delete method parameter UFCS`() = doTest("""
        struct S;
        impl S {
            fn foo/*caret*/(&self, a: u32, b: u32) {}
        }
        fn bar(s: S) {
            S::foo(&s, 0, 1);
        }
    """, """
        struct S;
        impl S {
            fn foo/*caret*/(&self, b: u32) {}
        }
        fn bar(s: S) {
            S::foo(&s, 1);
        }
    """) {
        parameters.removeAt(0)
    }

    fun `test swap parameters with comments`() = doTest("""
        fn foo/*caret*/( /*a0*/ a /*a1*/ : u32 /*a2*/ , /*b0*/ b: u32 /*b1*/ ) {}
        fn bar() {
            foo(0, 1);
        }
    """, """
        fn foo(/*b0*/ b: u32 /*b1*/, /*a0*/ a /*a1*/ : u32 /*a2*/) {}
        fn bar() {
            foo(1, 0);
        }
    """) {
        swapParameters(0, 1)
    }

    fun `test swap arguments with comments`() = doTest("""
        fn foo/*caret*/(a: u32, b: u32) {}
        fn bar() {
            foo( /*a0*/ 0 /*a1*/  /*a2*/ , /*b0*/ 1 /*b1*/ );
        }
    """, """
        fn foo(b: u32, a: u32) {}
        fn bar() {
            foo(/*b0*/ 1 /*b1*/, /*a0*/ 0 /*a1*/  /*a2*/);
        }
    """) {
        swapParameters(0, 1)
    }

    fun `test multiple move`() = doTest("""
        fn foo/*caret*/(a: u32, b: u32, c: u32) {}
        fn bar() {
            foo(0, 1, 2);
        }
    """, """
        fn foo(b: u32, c: u32, a: u32) {}
        fn bar() {
            foo(1, 2, 0);
        }
    """) {
        swapParameters(0, 1)
        swapParameters(1, 2)
    }

    fun `test swap back`() = doTest("""
        fn foo/*caret*/(a: u32, b: u32, c: u32) {}
        fn bar() {
            foo(0, 1, 2);
        }
    """, """
        fn foo(a: u32, b: u32, c: u32) {}
        fn bar() {
            foo(0, 1, 2);
        }
    """) {
        swapParameters(0, 1)
        swapParameters(1, 0)
    }

    fun `test move and add parameter`() = doTest("""
        fn foo/*caret*/(a: u32, b: u32) {}
        fn bar() {
            foo(0, 1);
        }
    """, """
        fn foo(b: u32, a: u32) {}
        fn bar() {
            foo(1, );
        }
    """) {
        parameters[0] = parameters[1]
        parameters[1] = parameter("a", createType("u32"))
    }

    fun `test rename parameter ident with ident`() = doTest("""
        fn foo/*caret*/(a: u32) {
            let _ = a;
            let _ = a + 1;
        }
    """, """
        fn foo(b: u32) {
            let _ = b;
            let _ = b + 1;
        }
    """) {
        parameters[0].patText = "b"
    }

    fun `test rename parameter complex pat with ident`() = doTest("""
        fn foo/*caret*/((a, b): (u32, u32)) {
            let _ = a;
        }
    """, """
        fn foo(x: (u32, u32)) {
            let _ = a;
        }
    """) {
        parameters[0].patText = "x"
    }

    fun `test rename parameter ident with complex pat`() = doTest("""
        fn foo/*caret*/(a: (u32, u32)) {
            let _ = a;
        }
    """, """
        fn foo((x, y): (u32, u32)) {
            let _ = a;
        }
    """) {
        parameters[0].patText = "(x, y)"
    }

    fun `test change parameter type`() = doTest("""
        fn foo/*caret*/(a: u32) {}
    """, """
        fn foo(a: i32) {}
    """) {
        parameters[0].typeText = createType("i32")
    }

    fun `test add async`() = doTest("""
        fn foo/*caret*/(a: u32) {}
    """, """
        async fn foo(a: u32) {}
    """) {
        isAsync = true
    }

    fun `test remove async`() = doTest("""
        async fn foo/*caret*/(a: u32) {}
    """, """
        fn foo(a: u32) {}
    """) {
        isAsync = false
    }

    fun `test add unsafe`() = doTest("""
        fn foo/*caret*/(a: u32) {}
    """, """
        unsafe fn foo(a: u32) {}
    """) {
        isUnsafe = true
    }

    fun `test remove unsafe`() = doTest("""
        unsafe fn foo/*caret*/(a: u32) {}
    """, """
        fn foo(a: u32) {}
    """) {
        isUnsafe = false
    }

    fun `test add async unsafe and visibility`() = doTest("""
        fn foo/*caret*/(a: u32) {}
    """, """
        pub async unsafe fn foo(a: u32) {}
    """) {
        isAsync = true
        isUnsafe = true
        visibility = createVisibility("pub")
    }

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test import return type in different module`() = doTest("""
        mod foo {
            pub struct S;
                     //^
        }
        mod bar {
            fn baz/*caret*/() {}
        }
    """, """
        mod foo {
            pub struct S;
                     //^
        }
        mod bar {
            use crate::foo::S;

            fn baz/*caret*/() -> S {}
        }
    """) {
        returnTypeDisplay = referToType("S", findElementInEditor<RsStructItem>())
    }

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test import parameter type in different module`() = doTest("""
        mod foo {
            pub struct S;
                     //^
        }
        mod bar {
            fn baz/*caret*/() {}
        }
    """, """
        mod foo {
            pub struct S;
                     //^
        }
        mod bar {
            use crate::foo::S;

            fn baz/*caret*/(s: S) {}
        }
    """) {
        parameters.add(parameter("s", referToType("S", findElementInEditor<RsStructItem>())))
    }

    fun `test name conflict module`() = checkConflicts("""
        fn foo/*caret*/() {}
        fn bar() {}
    """, setOf("The name bar conflicts with an existing item in main.rs (in test_package)")) {
        name = "bar"
    }

    fun `test name conflict impl`() = checkConflicts("""
        struct S;

        impl S {
            fn foo/*caret*/() {}
            fn bar() {}
        }
    """, setOf("The name bar conflicts with an existing item in impl S (in test_package)")) {
        name = "bar"
    }

    fun `test name conflict trait`() = checkConflicts("""
        struct S;
        trait Trait {
            fn foo/*caret*/();
            fn bar();
        }
    """, setOf("The name bar conflicts with an existing item in Trait (in test_package)")) {
        name = "bar"
    }

    fun `test visibility conflict function call`() = checkConflicts("""
        mod foo {
            pub fn bar/*caret*/() {}
        }
        fn baz() {
            foo::bar();
        }
    """, setOf("The function will not be visible from test_package after the refactoring")) {
        visibility = null
    }

    fun `test visibility conflict method call`() = checkConflicts("""
        mod foo {
            pub struct S;
            impl S {
                pub fn bar/*caret*/(&self) {}
            }
        }
        mod foo2 {
            fn baz(s: super::foo::S) {
                s.bar();
            }
        }
    """, setOf("The function will not be visible from test_package::foo2 after the refactoring")) {
        visibility = null
    }

    fun `test no visibility conflict restricted mod`() = doTest("""
        mod foo2 {
            mod foo {
                fn bar/*caret*/() {}
            }
            fn baz() {
                foo::bar();
            }
        }

    """, """
        mod foo2 {
            mod foo {
                pub(in super) fn bar/*caret*/() {}
            }
            fn baz() {
                foo::bar();
            }
        }

    """) {
        visibility = createVisibility("pub(in super)")
    }

    private val overriddenMethodWithUsagesBefore: String = """
        trait Trait {
            fn foo/*trait*/(&self);
        }

        struct S;
        impl Trait for S {
            fn foo/*impl*/(&self) {}
        }

        fn bar1(t: &dyn Trait) {
            t.foo();
        }
        fn bar2(s: S) {
            s.foo();
        }
        fn bar3<T: Trait>(t: &T) {
            t.foo();
        }
    """

    private val overriddenMethodWithUsagesAfter: String = """
        trait Trait {
            fn bar(&self) -> u32;
        }

        struct S;
        impl Trait for S {
            fn bar(&self) -> u32 {}
        }

        fn bar1(t: &dyn Trait) {
            t.bar();
        }
        fn bar2(s: S) {
            s.bar();
        }
        fn bar3<T: Trait>(t: &T) {
            t.bar();
        }
    """

    fun `test change overridden methods and usages when invoked on trait`() = doTest(
        overriddenMethodWithUsagesBefore.replace("/*trait*/", "/*caret*/").replace("/*impl*/", ""),
        overriddenMethodWithUsagesAfter
    ) {
        name = "bar"
        returnTypeDisplay = createType("u32")
    }

    fun `test change overridden methods and usages when invoked on impl`() = doTest(
        overriddenMethodWithUsagesBefore.replace("/*impl*/", "/*caret*/").replace("/*trait*/", ""),
        overriddenMethodWithUsagesAfter
    ) {
        name = "bar"
        returnTypeDisplay = createType("u32")
    }

    private fun RsChangeFunctionSignatureConfig.swapParameters(a: Int, b: Int) {
        val param = parameters[a]
        parameters[a] = parameters[b]
        parameters[b] = param
    }

    private fun createVisibility(vis: String): RsVis = RsPsiFactory(project).createVis(vis)
    private fun createType(text: String): RsTypeReference = RsPsiFactory(project).createType(text)
    private fun parameter(patText: String, type: RsTypeReference): Parameter {
        val factory = RsPsiFactory(project)
        return Parameter(
            factory,
            patText,
            type
        )
    }

    /**
     * Refer to existing type in the test code snippet.
     */
    private fun referToType(text: String, context: RsElement): RsTypeReference
        = RsTypeReferenceCodeFragment(myFixture.project, text, context).typeReference!!

    private fun doTest(
        @Language("Rust") code: String,
        @Language("Rust") expected: String,
        modifyConfig: RsChangeFunctionSignatureConfig.() -> Unit
    ) {
        withMockChangeFunctionSignature({ config ->
            modifyConfig.invoke(config)
        }) {
            checkEditorAction(code, expected, "ChangeSignature", trimIndent = false)
        }
    }

    private fun checkConflicts(
        @Language("Rust") code: String,
        expectedConflicts: Set<String>,
        modifyConfig: RsChangeFunctionSignatureConfig.() -> Unit
    ) {
        try {
            doTest(code, code, modifyConfig)
            if (expectedConflicts.isNotEmpty()) {
                error("No conflicts found, expected $expectedConflicts")
            }
        }
        catch (e: BaseRefactoringProcessor.ConflictsInTestsException) {
            assertEquals(expectedConflicts, e.messages.toSet())
        }
    }

    private fun checkError(@Language("Rust") code: String, errorMessage: String) {
        try {
            checkEditorAction(code, code, "ChangeSignature")
            error("No error found, expected $errorMessage")
        } catch (e: Exception) {
            assertEquals(errorMessage, e.message)
        }
    }
}
