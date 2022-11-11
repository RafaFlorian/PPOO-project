package com.account;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import javax.lang.model.type.ArrayType;

public class Main 

{
	
	
	
	public static void main(String args[]) throws IOException 
	
	{
	
	Account acc = new Account("1", 300.0, "John", "0787613414", "a.andu60@gmail.com");
	
	System.out.printf("---------Bun venit in E-banking, %s!---------\n", acc.getName());
	System.out.printf("\nInsereaza in consola cifra corespunzatoare optiunii dorite din meniu:", acc.getName());
	
	int option = Meniu.meniu_principal();
	switch (option) {
	
	case 1:
		Meniu.date_asoc_cont();
	case 2:
		Meniu.istoric_tranzactii();
	case 3:
		Meniu.Carduri();
	case 4:
		Meniu.tranfera();
	case 5:
		Meniu.adauga_bani();
		
	
	}
		
		
}
}
