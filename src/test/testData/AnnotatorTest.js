// Example 1
var a = <warning>"prop" in 42</warning>; // other issue

// Example 2
function Foo() {
}
var x = new Foo();
var b = <warning>x instanceof "string"</warning>; // here is the issue that behaves strange
