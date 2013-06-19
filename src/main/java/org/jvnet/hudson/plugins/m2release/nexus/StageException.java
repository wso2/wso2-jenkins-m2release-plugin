/*
 * The MIT License
 * 
 * Copyright (c) 2010, NDS Group Ltd., James Nord
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jvnet.hudson.plugins.m2release.nexus;

import java.io.IOException;

/**
 * Exception that indicates something has gone wrong with a staging request.
 * 
 * @author teilo
 * 
 */
public class StageException extends IOException {

	private static final long serialVersionUID = 3414907597198914915L;

	/**
	 * Constructs an {@code IOException} with the specified detail message and cause.
	 * 
	 * @param message
	 *        the message that should be shown to the end user to explain ehat went wrong.
	 * @param cause
	 *        the root cause of the issue.
	 */
	public StageException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Constructs a {@code StageException} with the specified detail message.
	 * 
	 * @param message
	 *        the message that should be shown to the end user to explain what went wrong.
	 */
	public StageException(String message) {
		super(message);
	}

	/**
	 * Constructs a {@code StageException} with the specified cause and a detail message of {@code (cause==null ? null :
	 * cause.toString())}
	 * 
	 * @param cause
	 *        the root cause of the issue.
	 */
	public StageException(Throwable cause) {
		super(cause);
	}

}
