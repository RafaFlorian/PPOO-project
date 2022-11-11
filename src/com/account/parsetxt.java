package com.account;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/**
 * Clasa parsetxt extinde clasa readtxt.
 * Scopul clasei este de a parsa datele din Array-urile returnare in urma citirii lor din txt
 * si de a le salva in diverse colectii sau vectori.
  */
public class parsetxt extends readtxt{

	/**
	 * Metoda tranzactii returneaza o colectie de tip map de string-uri si int.
	 * Aici sunt salvate datele din txt-ul Istoric plati.

	 * */
	@SuppressWarnings("unchecked")
	public static Map<String, List> tranzactii() throws IOException {
	
	String[] Tranzactii = readtxt.read_txt("Istoric plati.txt");
	
	ArrayList<String> BeneficiarTranzactie = new ArrayList<String>();
	ArrayList<Integer> ValoareTranzactie = new ArrayList<Integer>();
	@SuppressWarnings("rawtypes")
	Map<String, List> Tranzacii_map = new HashMap<String, List>();
	
	/**
	 * Se creaza cele doua chei de mapare
	 * */
	Tranzacii_map.put("bt", BeneficiarTranzactie);
	Tranzacii_map.put("vt", ValoareTranzactie);
	
	/**
	 * Se parseaza fiecare linie a variabilei String tranzactii.
	 * Datele sunt apoi adaugate prin metoda add a colectiei, conform cheii, in obiectul Tranzactii.
	 * */
	for (int i = 0; i <= Tranzactii.length - 1 ; i++) {
		
		
		if ( Tranzactii[i].split(" ")[0].charAt(0) == '-')
		{
			Tranzacii_map.get("vt").add(Integer.parseInt(Tranzactii[i].split(" ")[0].split("-")[1])*(-1));
			Tranzacii_map.get("bt").add(Tranzactii[i].split(" ")[1]);
		}
		
		else {
			Tranzacii_map.get("vt").add(Integer.parseInt(Tranzactii[i].split(" ")[0]));
			Tranzacii_map.get("bt").add(Tranzactii[i].split(" ")[1]);
		}
		
	
		
	}
	return Tranzacii_map;
	

}
	
	@SuppressWarnings("rawtypes")
	public static Map<String, List> carduri() throws IOException {
		
		String[] Carduri = readtxt.read_txt("Carduri.txt");
		
		ArrayList<String> Card = new ArrayList<String>();
		ArrayList<Integer> SoldCard = new ArrayList<Integer>();
		@SuppressWarnings("rawtypes")
		Map<String, List> cards_map = new HashMap<String, List>();
		
		cards_map.put("cd", Card);
		cards_map.put("sc", SoldCard);
		
		for (int i = 0; i <= Carduri.length - 1 ; i++) {
			
			
			cards_map.get("sc").add(Integer.parseInt(Carduri[i].split(" ")[1]));
			cards_map.get("cd").add(Carduri[i].split(" ")[0]);

			
		}
		return cards_map;
	
	}
	
	/**
	 *Metoda matrix_card returneaza un array de int-uri bidimensional, unde sunt salvate informariile din txt-ul Carduri.
	 
	 */
	
	public static int[][] matrix_card() throws IOException
	
	{
		String[] Carduri = readtxt.read_txt("Carduri.txt");
		int[][] Matrix = new int[Carduri.length][Carduri.length];
		
		for (int i=0; i < Carduri.length; i++)
		{
			for(int j=0; j <= 1; j++)
					
			{
				System.out.println(Carduri[i].split(" ")[j]);
				Matrix[i][j] = Integer.parseInt(Carduri[i].split(" ")[j]);
			}
		}
		
		return Matrix;
	}
	/**
	 * Metoda VectorProdServ returneaza un vector de string-uri, unde sunt salvate datele din txt-ul Servicii bancare.*/
	public static Vector<String> VectorProdServ() throws IOException
	{
		
		String[] SV = readtxt.read_txt("Servicii bancare.txt");
		Vector<String> vector = new Vector<String>();
		
		try {
		
			for (int i=0; i < SV.length; i++)
			{
				vector.add(0, SV[i]);
			}

		} catch (ArrayIndexOutOfBoundsException e)
		{
			System.out.println("Nu exista nici un serviciu in lista");
		}
		
		return vector;
	}
	
}