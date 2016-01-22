A simple collection of utilities that I've slowly been building as I need them for various
projects.  This is intended to contain things that are likely to be reusable across projects.

The biggest component (or at least the most frequently used and reusable) is FileUtil and related
classes.  This is an object that abstracts away the interface with the file system, for two
purposes: (1) to handle in a simple one-line call some common patterns with interacting with the
filesystem, like reading a set of integers from a file. And (2) to allow my code that interacts
with the filesystem to be testable, without having to have BufferedReader methods that are the
only parts that are tested.  I started out with the BufferedReader testing route, and ended up
here because it just worked better with all of the things I wanted to test.


# Change log

## Version 1.2.4:

- Added simple methods for reading and writing binary files, and for deleting directories, to
  FileUtil.

## Version 1.2.3:

- Use trove for immutable dictionaries, trying to further decrease memory usage and increase speed.

- Trying to get more efficient parsing of large files.  Now you can read integers from a TSV file
  without creating a ton of intermediate strings.

## Version 1.2.2:

- Improved the Index/Dictionary API, so you can have a non-mutable (and thus much more efficient)
  Dictionary.

## Version 1.2.1:

- Added the ability to enforce a set of options to JsonHelper, plus a minor test fix.

## Version 1.2:

- Moved FileUtil from java to scala, including moving to scala collections.  This will mean any
  code upgrading to this version of the library will likely have to change code, to remove asScala
(or add asJava, but why?) when interacting with FileUtil.

- Allows for parallel processing of files, where we can do some kind of map or flatMap for
  each line in the file, without having to read the whole file into memory first.

- Added tests for FileUtil and FakeFileUtil.

## Version 1.1:

- Added my SpecFileReader and JsonHelper, classes that deal with reading specification files in a
  JSON format, for configuring the behavior of code.
