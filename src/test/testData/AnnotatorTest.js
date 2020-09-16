// Example 1
var a = <weak_warning>"prop" in 42</weak_warning>; // other issue

// Example 2
function Foo() { }
var x = new Foo();
var b = <weak_warning>x instanceof "string"</weak_warning>; // here is the issue that behaves strange
