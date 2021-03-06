#!/usr/bin/env bash

# Learn from my mistakes:
#  If you edit the csv's in Excel, be extremely careful when you save.   I've seen at least two problems:
#    1.  The dates get screwed up.  I had to manually set the dates to yyyy-mm-dd format, or it changes them to mm/dd/yyyy
#    2.  It seems to tweak the use of "" for escaping fields, so that while the csv looks the same in Excel, the ascii has changed.

# This runs prime in a variety of ways on csv files and compare outputs to actuals

outputdir=target/csv_test_files
expecteddir=./src/test/csv_test_files/expected
expected_pima=$expecteddir/simplereport-pima.csv
expected_az=$expecteddir/simplereport-az.csv
#expected_az=$expecteddir/junk-az.csv
starter_schema=primedatainput/pdi-covid-19

RED='\033[0;31m'
NC='\033[0m' # No Color

# For testing the unhappy path

mkdir -p $outputdir

# Call this after calling .prime ... to generate output data.  Use this to parse the text output to extract the filename
# Sets a global variable $filename to the last string that matches $match_string in the text $prime_output
filename=0
function parse_prime_output_for_filename {
  prime_output=$1
  match_string=$2
  filename="File_not_found"
  ready=0
  for name in $prime_output ; do
    if grep -q "Creating" <<< $name ; then
      ready=1
    fi
    if [ $ready -eq 1 ] ; then 
      if grep -q $match_string <<< $name ; then
        filename=$name
      fi
    fi
  done

  if [ -f $filename ] ; then
    printf "Data generation TEST PASSED: successfully generated data file $filename\n\n"
  else 
    printf "${RED}*** ERROR ***: ./prime did not generate a file that matches $match_string${NC}\n"
    printf "Output of Prime run is: $prime_output\n"
    exit
  fi
}

function compare_files {
  schemaName=$1
  expected=$2
  actual=$3
  diff $expected $actual >/dev/null
  retval=$?
  if [ $retval -gt 0 ] ; then 
    printf "${RED}*** ERROR ***: DIFFERENCES FOUND for $schemaName.  Run this to see the diff:${NC}\n"
    printf "diff $expected $actual\n"
  else
    printf "File comparison TEST PASSED: No differences found in $schemaName data. This is the $schemaName output file:\n\t$actual\n\n"
  fi
  echo
}


echo First run our canned test file simplereport.csv thru the gauntlet
text=$(./prime --input_schema $starter_schema --input ./src/test/csv_test_files/input/simplereport.csv --output_dir $outputdir --route)

parse_prime_output_for_filename "$text" "/az"
actual_az=$filename
compare_files "SimpleReport->AZ"   $expected_az   $actual_az

parse_prime_output_for_filename "$text" "/pima"
actual_pima=$filename
compare_files "SimpleReport->PIMA" $expected_pima $actual_pima

# Add tx.

# Now read the data back in to their own schema and export again.
# AZ again
echo Test sending AZ data into its own Schema:
text=$(./prime --input_schema az/az-covid-19 --input $actual_az --output_dir $outputdir)
parse_prime_output_for_filename "$text" "/az"
actual_az2=$filename
compare_files "AZ->AZ" $expected_az $actual_az2

# Pima again
echo Test sending Pima data into its own Schema:
text=$(./prime --input_schema az/pima-az-covid-19 --input $actual_pima --output_dir $outputdir)
parse_prime_output_for_filename "$text" "/pima"
actual_pima2=$filename
compare_files "PIMA->PIMA" $expected_pima $actual_pima2

echo And now generate some fake simplereport data
text=$(./prime --input_fake 50 --input_schema $starter_schema --output_dir $outputdir)
parse_prime_output_for_filename "$text" "/pdi-covid-19"
fake_data=$filename

echo Now send that fake data thru the router.
# Note that there's no actuals to compare to here.
text=$(./prime --input_schema $starter_schema --input $fake_data --output_dir $outputdir --route)
# Find the AZ file
parse_prime_output_for_filename "$text" "/az"
actual_az3=$filename
# Find the Pima file
parse_prime_output_for_filename "$text" "/pima"
actual_pima3=$filename

echo Now send _those_ results back in to their own schema and export again!
# AZ again again.  This time we can compare the two actuals.
echo Test sending AZ data generated from fake data into its own Schema:
text=$(./prime --input_schema az/az-covid-19 --input $actual_az3 --output_dir $outputdir)
parse_prime_output_for_filename "$text" "/az"
actual_az4=$filename
compare_files "AZ->AZ" $actual_az3 $actual_az4

# Pima again again.  Compare the two actuals
echo Test sending Pima data generated from fake data into its own Schema:
text=$(./prime --input_schema az/pima-az-covid-19 --input $actual_pima3 --output_dir $outputdir)
parse_prime_output_for_filename "$text" "/pima"
actual_pima4=$filename
compare_files "PIMA->PIMA" $actual_pima3 $actual_pima4






