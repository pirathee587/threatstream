from threatstream_processor.replay import dataset_directory

def test_dataset_directory_is_untracked_data_location():
    assert dataset_directory().parts[-2:] == ("data", "CIC-IDS2017")
