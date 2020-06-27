// Example 1
var a = "prop" in 42; // other issue

// Example 2
function Foo() {
}
var x = new Foo();
var b = x instanceof "string"; // here is the issue that behaves strange
