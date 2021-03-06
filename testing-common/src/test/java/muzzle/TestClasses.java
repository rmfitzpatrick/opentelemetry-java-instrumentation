/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package muzzle;

import net.bytebuddy.asm.Advice;

public class TestClasses {

  public static class MethodBodyAdvice {
    @Advice.OnMethodEnter
    public static void methodBodyAdvice() {
      A a = new A();
      SomeInterface inter = new SomeImplementation();
      inter.someMethod();
      a.b.aMethod("foo");
      a.b.aMethodWithPrimitives(false);
      a.b.aMethodWithArrays(new String[0]);
      B.aStaticMethod();
      A.staticB.aMethod("bar");
    }

    public static class A {
      public B b = new B();
      protected Object protectedField = null;
      private Object privateField = null;
      public static B staticB = new B();
    }

    public static class B {
      public String aMethod(String s) {
        return s;
      }

      public void aMethodWithPrimitives(boolean b) {}

      public Object[] aMethodWithArrays(String[] s) {
        return s;
      }

      private void privateStuff() {}

      protected void protectedMethod() {}

      public static void aStaticMethod() {}
    }

    public static class B2 extends B {
      public void stuff() {
        B b = new B();
        b.protectedMethod();
      }
    }

    public static class A2 extends A {}

    public interface SomeInterface {
      void someMethod();
    }

    public static class SomeImplementation implements SomeInterface {
      @Override
      public void someMethod() {}
    }

    public static class SomeClassWithFields {
      public int instanceField = 0;
      public static int staticField = 0;
      public final int finalField = 0;
    }

    public interface AnotherInterface extends SomeInterface {}
  }

  public static class LdcAdvice {
    public static void ldcMethod() {
      MethodBodyAdvice.A.class.getName();
    }
  }
}
