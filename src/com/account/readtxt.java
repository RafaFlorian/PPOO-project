package com.account;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
/**
 In aceasta clasa este definita o metoda publica prin care se citesc fisierele de tip txt
*/
public class readtxt {
	
	/**
	 Metoda read_txt va returna un array de stringuri.
	 Metoda ia ca parametru calea fisierului txt.
	 Se creeaza un obiect de tip scanner folosit pt parsarea liniilor din txt cu delimitatorul ",".
	 Fiecare linie e adaugata in lista de stringuri definita, iar apoi transformata in arrays, prin metoda toArray.
	 * */
	public static String[] read_txt(String path) throws IOException{
		

		List<String> listOfStrings
			= new ArrayList<String>();
	

		Scanner sc = new Scanner(new FileReader(path))
						.useDelimiter(",");
		String str;
	

		while (sc.hasNext()) {
			str = sc.next();
		

			listOfStrings.add(str);
		}
	

		String[] array
			= listOfStrings.toArray(new String[0]);
	
		
		return array;
		
		
		
		}
	
		
	}
	
	
	

