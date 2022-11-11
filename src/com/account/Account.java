package com.account;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
/**
 Definire clasa Account cu constructor format din number, blanace, name, phone, email.
 Definire geteri si seteri
 */
public class Account {
	
	private String Number;
	private double Balance;
	private String Name;
	private String PhoneNumber;
	private String Email;
	
	public Account(String c, double Balance, String Name,
			       String PhoneNumber, String Email)
	{
		
		this.Number = c;
		this.Balance = Balance;
		this.Name = Name;
		this.PhoneNumber = PhoneNumber;
		this.Email = Email;

	}
	
	public String getNumber() {
		return Number;
	}

	public void setNumber(String number) {
		Number = number;
	}

	public double getBalance() {
		return Balance;
	}

	public void setBalance(double balance) {
		Balance = balance;
	}

	public String getName() {
		return Name;
	}

	public void setName(String name) {
		Name = name;
	}

	public String getPhoneNumber() {
		return PhoneNumber;
	}

	public void setPhoneNumber(String phoneNumber) {
		PhoneNumber = phoneNumber;
	}

	public String getEmail() {
		return Email;
	}

	public void setEmail(String email) {
		Email = email;
	}
/**
 Metoda ce reprezinta o verificare custom pentru valorile introduse de la tastatura pentru meniu
 * 
  
 */
static void checkInput(int par) {
		
	List<Integer> a = new ArrayList<Integer>();
	Collections.addAll(a, 1, 2, 3, 4, 5, 6);
	
	if (a.contains(par)) {
		
		System.out.println("------");
		
	}
	else {
		
		throw new ArithmeticException("Va rugam sa selectati o optiune valida.");
	}
		}


}
