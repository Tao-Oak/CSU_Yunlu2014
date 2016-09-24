package csu.common.test;

public class TestString {

	public static void main(String[] args) {
		String str = "abc";
		String str_1 = "abc";
		String result = null;
		if (str.equals(str_1))
			result = " = ";
		else
			result = " != ";
		System.out.println(str + result + str_1);
	}
}
