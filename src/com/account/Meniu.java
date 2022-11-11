package com.account;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**Clasa Meniu contine metodele aferente fiecarei optiune din meniul principal al aplicatiei.*/
public class Meniu {
	
	/** 
	 Se citeste de la tastatura optiunea pe care o doreste utilizatorul si se returneaza.
	 Se verifica daca utilizatorul a introdus o val intre 1-6.
	 * 
	 * */
	static int meniu_principal() {

		Scanner keyboard = new Scanner(System.in);
		System.out.printf("\n\n1.Date asociate contului\n"
						+ "2.Istoric tranzatii\n"
						+ "3.Carduri\n"
						+ "4.Transfera\n"
						+ "5.Adauga bani\n"
						+ "6.Schimba\n");
		int option = keyboard.nextInt();
		try  {
			
			Account.checkInput(option);
		} catch (NumberFormatException e) {
			System.out.println("Ceva nu a mers bine.");
		} 
			
		return option;
		
	}
	
	/** 
	 Metoda de tip void care afiseaza utlizatorului informatiile contului sau.
	 Utilizatorul poate introduce 1 de la tastatura pentru a se intorace la meniul principal.
	 */
	static void date_asoc_cont() throws IOException {
		
		Account acc = new Account("18694", 300.0, "John", "0787613414", "a.andu60@gmail.com");
		
		System.out.printf("\nDatele asociate contului tau:\n\nNume:%s\nNumar"
				+ " Cont:%s\nNumar de telefon: %s\nAdresa de mail:%s",
				acc.getName(), acc.getNumber(), acc.getPhoneNumber(), acc.getEmail());
		
		Scanner keyboard = new Scanner(System.in);
		System.out.printf("\n\nApasa 1 pentru a te intoarce la meniul principal.\n\n");
		int option = keyboard.nextInt();
		Main.main(null);

		
	}
	
	/** 
	 Metoda de tip void care afiseaza utlizatorului informatiile despre tranzactiile efectuate.
	 Utilizatorul poate introduce 1 de la tastatura pentru a se intorace la meniul principal.* 
	 */
	static void istoric_tranzactii() throws IOException {
		
		String[] array =  readtxt.read_txt("Istoric plati.txt");

		String s = new String();
		for (String tranzactie:array){
			
			s = s + tranzactie + "\n";
		}
		
		
		System.out.printf("\n\nIstoric tranzactii:\n\n%s", s);
		
		Scanner keyboard = new Scanner(System.in);
		System.out.printf("\n\nApasa 1 pentru a te intoarce la meniul principal.\n\n");
		int option = keyboard.nextInt();
		Main.main(null);
		
	}
	
	/** 
	 Metoda de tip void care afiseaza utlizatorului informatiile despre cardurile detinute.
	 Utilizatorul poate introduce 1 de la tastatura pentru a se intorace la meniul principal.* 
	 */
	static void Carduri() throws IOException{
		
		int[][] cards = parsetxt.matrix_card();
		System.out.printf("\n\nCardurile dumneavoastra sunt:\n\n");
		for(int i=0; i< cards.length; i++) {
			
			System.out.printf("Card cu numarul %d - %d lei\n",cards[0][i],cards[1][i]);
			
		}
		
		Scanner keyboard = new Scanner(System.in);
		System.out.printf("\n\nApasa 1 pentru a te intoarce la meniul principal.\n\n");
		int option = keyboard.nextInt();
		Main.main(null);
	
	
	
	}
	/** Metoda care ciste numele benificiarului si suma transferata.
	 * Se vor adauga noile informatii adaugate la colectia definita.
	 * Se va actualiza fisierul txt.*/
	static void tranfera() throws IOException {
		
		Map<String,List> transf = parsetxt.tranzactii();

		
		System.out.printf("\n---Pentru a initia un transfer completeaza urmatoarele informatii:---\n\n");
		Scanner keyboard = new Scanner(System.in);
		
		System.out.printf("Beneficiar:\n");
		String beneficiar = keyboard.next();
		
		System.out.printf("Suma pe care doriti sa o transferati:\n");
		String suma = keyboard.next();
		
		
		System.out.printf("\n--Tranzactia a fost efectuata cu succes.--\n");
		
		
		System.out.printf("\n----------\nApasa 1 pentru a salva si a te intoarce la meniul principal.\n"
				+ "Apasa 2 Pentru a anula tranzactia si pentru a inchide aplicatia.\n");
		
		int option = keyboard.nextInt();
		switch(option) {
		case 1:
			
			transf.get("vt").add(suma);
			transf.get("bt").add(beneficiar);
			
			File myObj = new File("Istoric plati.txt"); 
		    myObj.delete();
		    String tranz = new String(); 
		    
		    String s = new String();
	
		    for (int i = 0; i <  readtxt.read_txt("Istoric plati.txt").length + 1; i++) {
		    	
		    	if (i == readtxt.read_txt("Istoric plati.txt").length) {
		    		
		    		s = s + "-"+transf.get("vt").get(i).toString() + " " + transf.get("bt").get(i);
		    	}
		    	else {
		    		s = s + transf.get("vt").get(i).toString() + " " + transf.get("bt").get(i)+",";
		    			}
		    }

		    
		    try {
	            File newTextFile = new File("Istoric plati.txt");

	            FileWriter fw = new FileWriter(newTextFile);
	            fw.write(s);
	            fw.close();

	        } catch (IOException iox) {
	            iox.printStackTrace();
	        }
		    
		    
			Main.main(null);
			break;
			
		case 2:
			System.exit(0);
			
		}


	}
	
	static void adauga_bani() throws IOException {
		
		Map<String,List> transf = parsetxt.tranzactii();

		
		System.out.printf("\n---Pentru a adauga bani in contul tau, te rog completeaza urmatoarele informatii:---\n\n");
		Scanner keyboard = new Scanner(System.in);
		
		System.out.printf("IBAN:\n");
		String beneficiar = keyboard.next();
		
		System.out.printf("Suma pe care doriti sa o adaugati:\n");
		String suma = keyboard.next();
		
		
		System.out.printf("\n--Tranzactia a fost efectuata cu succes.--\n");
		
		
		System.out.printf("\n----------\nApasa 1 pentru a salva si a te intoarce la meniul principal.\n"
				+ "Apasa 2 pentru a inchide aplicatia.\n");
		
		int option = keyboard.nextInt();
		switch(option) {
		case 1:
			
			transf.get("vt").add(suma);
			transf.get("bt").add(beneficiar);
			
			File myObj = new File("Istoric plati.txt"); 
		    myObj.delete();
		    String tranz = new String(); 
		    
		    String s = new String();
	
		    for (int i = 0; i <  readtxt.read_txt("Istoric plati.txt").length + 1; i++) {
		    	
		    	s = s + transf.get("vt").get(i).toString() + " " + transf.get("bt").get(i)+",";
		    }

		    try {
	            File newTextFile = new File("Istoric plati.txt");

	            FileWriter fw = new FileWriter(newTextFile);
	            fw.write(s);
	            fw.close();

	        } catch (IOException iox) {
	            iox.printStackTrace();
	        }
		    
		    
			Main.main(null);
			break;
			
		case 2:
			System.exit(0);
			
		}


	}
		
}


